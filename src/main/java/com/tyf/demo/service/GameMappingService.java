package com.tyf.demo.service;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.TriggerType;
import org.pmw.tinylog.Logger;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameMappingService {

    private static final long MOUSE_MOVE_TIMEOUT_MS = 500;
    private static final long MOUSE_MOVE_IGNORE_BASE_MS = 12;
    private static final float VIEW_TOUCH_MIN = 0.05f;
    private static final float VIEW_TOUCH_MAX = 0.95f;
    private static final float MOUSE_DELTA_EMA_ALPHA = 0.42f;
    private static final float MOUSE_DEADZONE_PX = 0.35f;
    private static final float MOUSE_DELTA_MAX_PX = 140f;
    private static final long TARGET_EVENT_DT_NS = 8_000_000L; // 125Hz
    private static final long PTR_JOYSTICK = 1001L;
    private static final long PTR_MOUSE_VIEW = 1002L;
    private static final long PTR_LEFT_LONG = 1003L;
    private static final long PTR_RIGHT_AIM = 1004L;
    private static final long PTR_CLICK_BASE = 2000L;
    private static final long CLICK_UP_DELAY_MS = 35L;

    private static int currentVideoWidth = 1080;
    private static int currentVideoHeight = 1920;

    private static final Set<Integer> nonRepeatKeysDown = new HashSet<>();
    private static final Map<Integer, MappingEntry> keyboardHoldActive = new HashMap<>();

    private static final boolean[] wasdDown = new boolean[4];

    private static boolean joystickTouchActive;
    private static int lastJoystickX;
    private static int lastJoystickY;

    private static volatile boolean leftButtonHoldActive;
    private static MappingEntry activeLeftButtonEntry;
    private static ScheduledExecutorService actionScheduler;
    private static ScheduledExecutorService mouseMoveScheduler;

    // 鼠标移动视角相关状态
    private static boolean mouseMoveTouching = false;
    private static float mouseMoveX = 0.5f;
    private static float mouseMoveY = 0.5f;
    private static long ignoreMouseMoveUntilMs = 0L;
    private static float filteredDeltaX = 0f;
    private static float filteredDeltaY = 0f;
    private static long lastMouseEventNs = 0L;
    private static ScheduledFuture<?> mouseMoveTimeoutFuture;

    private GameMappingService() {}

    public static void resetState() {
        nonRepeatKeysDown.clear();
        releaseAllKeyboardHoldMappings();
        for (int i = 0; i < wasdDown.length; i++) {
            wasdDown[i] = false;
        }
        if (joystickTouchActive) {
            ControlService.sendTouchUp(PTR_JOYSTICK, lastJoystickX, lastJoystickY);
            joystickTouchActive = false;
        }
        leftButtonHoldActive = false;
        activeLeftButtonEntry = null;
        
        mouseMoveStopTouch();
        ignoreMouseMoveUntilMs = 0L;
        filteredDeltaX = 0f;
        filteredDeltaY = 0f;
        lastMouseEventNs = 0L;
        
        // Logger.debug("game mapping: state reset");
    }

    public static void updateVideoSize(int width, int height) {
        currentVideoWidth = width;
        currentVideoHeight = height;
    }

    public static void handleKeyPressed(int keyCode) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        int wasdIdx = wasdIndex(keyCode);
        if (wasdIdx >= 0) {
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                // Logger.info("game mapping: triggered - " + joy.getName() + " (WASD)");
                wasdDown[wasdIdx] = true;
                updateJoystickTouch(joy);
                return;
            }
        }

        if (nonRepeatKeysDown.contains(keyCode)) {
            return;
        }
        nonRepeatKeysDown.add(keyCode);

        MappingEntry entry = GameMappingConfig.findMappingByKeyCode(keyCode);
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        if (entry.getTriggerType() == TriggerType.KEYBOARD
                && entry.getType() == MappingType.CLICK
                && entry.getKeyboardPressMode() == GameMappingConfig.KeyboardPressMode.HOLD) {
            long pointerId = pointerIdForKeyboardHold(entry);
            int x = clampToScreenX(entry.getPhoneX());
            int y = clampToScreenY(entry.getPhoneY());
            ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            keyboardHoldActive.put(keyCode, entry);
            // Logger.info("game mapping: triggered - " + entry.getName() + " (键盘长按开始)");
            return;
        }

        executeMapping(entry);
    }

    public static void handleKeyReleased(int keyCode) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        int wasdIdx = wasdIndex(keyCode);
        if (wasdIdx >= 0) {
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                wasdDown[wasdIdx] = false;
                updateJoystickTouch(joy);
                return;
            }
        }

        nonRepeatKeysDown.remove(keyCode);
        MappingEntry hold = keyboardHoldActive.remove(keyCode);
        if (hold != null) {
            long pointerId = pointerIdForKeyboardHold(hold);
            int x = clampToScreenX(hold.getPhoneX());
            int y = clampToScreenY(hold.getPhoneY());
            ControlService.sendTouchUp(pointerId, x, y);
            // Logger.info("game mapping: triggered - " + hold.getName() + " (键盘长按结束)");
        }
    }

    private static int wasdIndex(int keyCode) {
        if (keyCode == KeyEvent.VK_W) {
            return 0;
        }
        if (keyCode == KeyEvent.VK_A) {
            return 1;
        }
        if (keyCode == KeyEvent.VK_S) {
            return 2;
        }
        if (keyCode == KeyEvent.VK_D) {
            return 3;
        }
        return -1;
    }

    private static void updateJoystickTouch(MappingEntry entry) {
        float dx = 0;
        float dy = 0;
        if (wasdDown[0]) {
            dy -= 1;
        }
        if (wasdDown[2]) {
            dy += 1;
        }
        if (wasdDown[1]) {
            dx -= 1;
        }
        if (wasdDown[3]) {
            dx += 1;
        }
        boolean any = dx != 0 || dy != 0;
        if (dx != 0 && dy != 0) {
            dx *= 0.70710678f;
            dy *= 0.70710678f;
        }

        int sw = Math.max(1, currentVideoWidth);
        int sh = Math.max(1, currentVideoHeight);
        int minSide = Math.min(sw, sh);
        float cx = entry.getPhoneX();
        float cy = entry.getPhoneY();
        float r = entry.getJoystickRadius();

        if (!any) {
            if (joystickTouchActive) {
                ControlService.sendTouchUp(PTR_JOYSTICK, lastJoystickX, lastJoystickY);
                joystickTouchActive = false;
            }
            return;
        }

        int px = (int) (cx * sw + dx * r * minSide);
        int py = (int) (cy * sh + dy * r * minSide);
        px = Math.max(0, Math.min(px, sw - 1));
        py = Math.max(0, Math.min(py, sh - 1));

        if (!joystickTouchActive) {
            ControlService.sendTouchDown(PTR_JOYSTICK, px, py, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            joystickTouchActive = true;
            lastJoystickX = px;
            lastJoystickY = py;
        } else {
            ControlService.sendTouchMove(PTR_JOYSTICK, px, py);
            lastJoystickX = px;
            lastJoystickY = py;
        }
    }

    public static void handleMouseMoved(int deltaX, int deltaY) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        MappingEntry entry = GameMappingConfig.getMouseMoveMapping();
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < ignoreMouseMoveUntilMs) {
            return;
        }

        int sensitivity = entry.getMouseSensitivity();

        if (!mouseMoveTouching) {
            mouseMoveStartTouch();
        }

        resetMouseMoveTimeout();

        long nowNs = System.nanoTime();
        long dtNs = lastMouseEventNs > 0 ? (nowNs - lastMouseEventNs) : TARGET_EVENT_DT_NS;
        lastMouseEventNs = nowNs;

        // 事件频率波动时做轻量时间归一化，避免速度忽快忽慢
        float dtScale = (float) TARGET_EVENT_DT_NS / Math.max(1f, (float) dtNs);
        dtScale = Math.max(0.6f, Math.min(1.8f, dtScale));

        float rawDx = deltaX * dtScale;
        float rawDy = deltaY * dtScale;
        // 尖峰限幅：防止回摆/丢帧导致单帧超大跳变
        rawDx = Math.max(-MOUSE_DELTA_MAX_PX, Math.min(MOUSE_DELTA_MAX_PX, rawDx));
        rawDy = Math.max(-MOUSE_DELTA_MAX_PX, Math.min(MOUSE_DELTA_MAX_PX, rawDy));

        filteredDeltaX = filteredDeltaX + MOUSE_DELTA_EMA_ALPHA * (rawDx - filteredDeltaX);
        filteredDeltaY = filteredDeltaY + MOUSE_DELTA_EMA_ALPHA * (rawDy - filteredDeltaY);
        // 微抖死区：静止时抑制极小抖动
        if (Math.abs(filteredDeltaX) < MOUSE_DEADZONE_PX) {
            filteredDeltaX = 0f;
        }
        if (Math.abs(filteredDeltaY) < MOUSE_DEADZONE_PX) {
            filteredDeltaY = 0f;
        }

        float nextX = mouseMoveX + filteredDeltaX * sensitivity / currentVideoWidth;
        float nextY = mouseMoveY + filteredDeltaY * sensitivity / currentVideoHeight;

        // 参考 QtScrcpy：到边缘后先结束当前视角触点，等待后续事件重建，避免频繁 up/down 造成跳变
        if (nextX < VIEW_TOUCH_MIN || nextX > VIEW_TOUCH_MAX || nextY < VIEW_TOUCH_MIN || nextY > VIEW_TOUCH_MAX) {
            mouseMoveStopTouch();
            ignoreNextMouseMoves(3);
            return;
        }

        mouseMoveX = nextX;
        mouseMoveY = nextY;

        int screenX = (int) (mouseMoveX * currentVideoWidth);
        int screenY = (int) (mouseMoveY * currentVideoHeight);

        ControlService.sendTouchMove(PTR_MOUSE_VIEW, screenX, screenY);
        // Logger.debug("game mapping: view moved delta=(" + deltaX + "," + deltaY + ") -> pos=(" + mouseMoveX + "," + mouseMoveY + ")");
    }

    public static void ignoreNextMouseMoves(int count) {
        if (count <= 0) {
            return;
        }
        long until = System.currentTimeMillis() + (long) count * MOUSE_MOVE_IGNORE_BASE_MS;
        ignoreMouseMoveUntilMs = Math.max(ignoreMouseMoveUntilMs, until);
        filteredDeltaX = 0f;
        filteredDeltaY = 0f;
        lastMouseEventNs = 0L;
    }

    private static void mouseMoveStartTouch() {
        if (mouseMoveTouching) {
            return;
        }

        MappingEntry entry = GameMappingConfig.getMouseMoveMapping();
        if (entry == null) {
            return;
        }

        float startX = entry.getPhoneX();
        float startY = entry.getPhoneY();
        if (startX <= 0 && startY <= 0) {
            startX = 0.5f;
            startY = 0.5f;
        }

        mouseMoveX = startX;
        mouseMoveY = startY;

        int screenX = (int) (startX * currentVideoWidth);
        int screenY = (int) (startY * currentVideoHeight);

        ControlService.sendTouchDown(PTR_MOUSE_VIEW, screenX, screenY, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        mouseMoveTouching = true;
        filteredDeltaX = 0f;
        filteredDeltaY = 0f;
        lastMouseEventNs = 0L;

        // Logger.debug("game mapping: view touch start at (" + startX + "," + startY + ")");
    }

    private static void mouseMoveStopTouch() {
        if (!mouseMoveTouching) {
            return;
        }

        int screenX = (int) (mouseMoveX * currentVideoWidth);
        int screenY = (int) (mouseMoveY * currentVideoHeight);

        ControlService.sendTouchUp(PTR_MOUSE_VIEW, screenX, screenY);
        mouseMoveTouching = false;
        cancelMouseMoveTimeout();
        filteredDeltaX = 0f;
        filteredDeltaY = 0f;
        lastMouseEventNs = 0L;

        // Logger.debug("game mapping: view touch stop");
    }

    private static void ensureMouseMoveScheduler() {
        if (mouseMoveScheduler == null || mouseMoveScheduler.isShutdown()) {
            mouseMoveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "game-mapping-view");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private static void ensureActionScheduler() {
        if (actionScheduler == null || actionScheduler.isShutdown()) {
            actionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "game-mapping-action");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private static void resetMouseMoveTimeout() {
        cancelMouseMoveTimeout();
        ensureMouseMoveScheduler();
        mouseMoveTimeoutFuture = mouseMoveScheduler.schedule(() -> {
            mouseMoveStopTouch();
        }, MOUSE_MOVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void cancelMouseMoveTimeout() {
        if (mouseMoveTimeoutFuture != null && !mouseMoveTimeoutFuture.isDone()) {
            mouseMoveTimeoutFuture.cancel(false);
        }
        mouseMoveTimeoutFuture = null;
    }

    public static void handleMousePressed(int button) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        if (button == MouseEvent.BUTTON3) {
            MappingEntry aim = GameMappingConfig.findMappingByMouseRight();
            if (aim != null && aim.isEnabled() && aim.getType() == MappingType.CLICK) {
                // Logger.info("game mapping: triggered - " + aim.getName());
                executeClickDown(PTR_RIGHT_AIM, aim.getPhoneX(), aim.getPhoneY());
            }
            return;
        }

        if (button != MouseEvent.BUTTON1) {
            return;
        }

        MappingEntry leftEntry = GameMappingConfig.findMappingByMouseLeft();
        if (leftEntry == null || !leftEntry.isEnabled()) {
            return;
        }
        activeLeftButtonEntry = leftEntry;
        leftButtonHoldActive = true;
        executeClickDown(PTR_LEFT_LONG, leftEntry.getPhoneX(), leftEntry.getPhoneY());
    }

    public static void handleMouseReleased(int button) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        if (button == MouseEvent.BUTTON3) {
            MappingEntry aim = GameMappingConfig.findMappingByMouseRight();
            if (aim != null && aim.isEnabled() && aim.getType() == MappingType.CLICK) {
                int x = (int) (currentVideoWidth * aim.getPhoneX());
                int y = (int) (currentVideoHeight * aim.getPhoneY());
                // Logger.info("game mapping: triggered - " + aim.getName() + " (释放)");
                ControlService.sendTouchUp(PTR_RIGHT_AIM, x, y);
            }
            return;
        }

        if (button != MouseEvent.BUTTON1) {
            return;
        }

        if (leftButtonHoldActive && activeLeftButtonEntry != null) {
            int x = clampToScreenX(activeLeftButtonEntry.getPhoneX());
            int y = clampToScreenY(activeLeftButtonEntry.getPhoneY());
            ControlService.sendTouchUp(PTR_LEFT_LONG, x, y);
        }
        leftButtonHoldActive = false;
        activeLeftButtonEntry = null;
    }

    private static void executeClickDown(long pointerId, float ratioX, float ratioY) {
        int x = clampToScreenX(ratioX);
        int y = clampToScreenY(ratioY);
        ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        // Logger.debug("game mapping: touch down at (" + ratioX + ", " + ratioY + ")");
    }

    private static void executeMapping(MappingEntry entry) {
        if (entry == null) {
            return;
        }

        // Logger.info("game mapping: triggered - " + entry.getName());

        switch (entry.getType()) {
            case CLICK:
                if (entry.getTriggerType() == TriggerType.MOUSE_RIGHT) {
                    return;
                }
                executeClick(entry);
                break;
            case MOUSE_MOVE:
            case JOYSTICK_WASD:
                break;
            default:
                Logger.warn("game mapping: unsupported type " + entry.getType());
                break;
        }
    }

    private static void executeClick(MappingEntry entry) {
        float ratioX = entry.getPhoneX();
        float ratioY = entry.getPhoneY();
        int x = clampToScreenX(ratioX);
        int y = clampToScreenY(ratioY);

        long pointerId = pointerIdForClick(entry);
        ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        ensureActionScheduler();
        int finalX = x;
        int finalY = y;
        actionScheduler.schedule(() -> ControlService.sendTouchUp(pointerId, finalX, finalY), CLICK_UP_DELAY_MS, TimeUnit.MILLISECONDS);

        // Logger.debug("game mapping: click at (" + ratioX + ", " + ratioY + ")");
    }

    private static long pointerIdForClick(MappingEntry entry) {
        if (entry == null) {
            return PTR_CLICK_BASE;
        }
        String seed = entry.getBuiltinId();
        if (seed == null || seed.isEmpty()) {
            seed = entry.getId();
        }
        if (seed == null || seed.isEmpty()) {
            seed = entry.getName();
        }
        int h = seed != null ? Math.abs(seed.hashCode()) : 1;
        return PTR_CLICK_BASE + (h % 900);
    }

    private static long pointerIdForKeyboardHold(MappingEntry entry) {
        return 3000L + (pointerIdForClick(entry) % 900L);
    }

    private static int clampToScreenX(float ratioX) {
        int x = (int) (currentVideoWidth * ratioX);
        return Math.max(0, Math.min(x, currentVideoWidth - 1));
    }

    private static int clampToScreenY(float ratioY) {
        int y = (int) (currentVideoHeight * ratioY);
        return Math.max(0, Math.min(y, currentVideoHeight - 1));
    }

    private static void releaseAllKeyboardHoldMappings() {
        for (MappingEntry hold : keyboardHoldActive.values()) {
            if (hold == null) {
                continue;
            }
            long pointerId = pointerIdForKeyboardHold(hold);
            ControlService.sendTouchUp(pointerId, clampToScreenX(hold.getPhoneX()), clampToScreenY(hold.getPhoneY()));
        }
        keyboardHoldActive.clear();
    }
}
