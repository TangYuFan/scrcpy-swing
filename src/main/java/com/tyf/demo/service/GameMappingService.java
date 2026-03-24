package com.tyf.demo.service;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.TriggerType;
import com.tyf.demo.service.mapping.BuiltinMappingIds;
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
    private static final float MOUSE_SENSITIVITY_MIN_GAIN = 0.12f;
    private static final long MOUSE_MOVE_PUMP_PERIOD_MS = 8L; // ~125Hz
    private static final float MOUSE_VIEW_STEP_MAX_NORM = 0.012f;
    private static final int MOUSE_VIEW_MAX_STEPS_PER_TICK = 6;
    private static volatile float mouseDeltaEmaAlpha = MOUSE_DELTA_EMA_ALPHA;
    private static volatile long mouseMoveIgnoreBaseMs = MOUSE_MOVE_IGNORE_BASE_MS;
    private static volatile float mouseDeadzonePx = MOUSE_DEADZONE_PX;
    private static volatile float mouseDeltaMaxPx = MOUSE_DELTA_MAX_PX;
    private static final long PTR_JOYSTICK = 1001L;
    private static final long PTR_MOUSE_VIEW = 1002L;
    private static final long PTR_LEFT_LONG = 1003L;
    private static final long PTR_RIGHT_AIM = 1004L;
    private static final long PTR_CLICK_BASE = 2000L;
    private static final long CLICK_UP_DELAY_MS = 35L;
    // 模拟“摇杆推得越远速度越快”：默认中等推杆，奔跑键=大推杆，静步键=小推杆
    private static final float JOYSTICK_NORMAL_SCALE = 0.78f;
    private static final float JOYSTICK_RUN_SCALE = 1.00f;
    private static final float JOYSTICK_WALK_SLOW_SCALE = 0.45f;
    private static final float JOYSTICK_RADIUS_MIN = 0.03f;
    private static final float JOYSTICK_RADIUS_MAX = 0.35f;

    private static int currentVideoWidth = 1080;
    private static int currentVideoHeight = 1920;

    private static final Set<Integer> nonRepeatKeysDown = new HashSet<>();
    private static final Map<Integer, MappingEntry> keyboardHoldActive = new HashMap<>();

    private static final boolean[] wasdDown = new boolean[4];
    private static volatile boolean runModifierDown;
    private static volatile boolean walkSlowModifierDown;

    private static boolean joystickTouchActive;
    private static int joystickCenterX;
    private static int joystickCenterY;
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
    private static float pendingMouseDx = 0f;
    private static float pendingMouseDy = 0f;
    private static ScheduledFuture<?> mouseMoveTimeoutFuture;
    private static ScheduledFuture<?> mouseMovePumpFuture;

    public enum RealtimeProfile {
        STABLE,
        BALANCED,
        RESPONSIVE
    }

    private GameMappingService() {}

    static {
        applyRealtimeProfile(RealtimeProfile.BALANCED);
    }

    public static void applyRealtimeProfile(RealtimeProfile profile) {
        if (profile == null) {
            profile = RealtimeProfile.BALANCED;
        }
        switch (profile) {
            case STABLE:
                mouseDeltaEmaAlpha = 0.32f;
                mouseMoveIgnoreBaseMs = 14L;
                mouseDeadzonePx = 0.45f;
                mouseDeltaMaxPx = 110f;
                break;
            case RESPONSIVE:
                mouseDeltaEmaAlpha = 0.56f;
                mouseMoveIgnoreBaseMs = 9L;
                mouseDeadzonePx = 0.25f;
                mouseDeltaMaxPx = 180f;
                break;
            case BALANCED:
            default:
                mouseDeltaEmaAlpha = MOUSE_DELTA_EMA_ALPHA;
                mouseMoveIgnoreBaseMs = MOUSE_MOVE_IGNORE_BASE_MS;
                mouseDeadzonePx = MOUSE_DEADZONE_PX;
                mouseDeltaMaxPx = MOUSE_DELTA_MAX_PX;
                break;
        }
    }

    public static void resetState() {
        nonRepeatKeysDown.clear();
        releaseAllKeyboardHoldMappings();
        for (int i = 0; i < wasdDown.length; i++) {
            wasdDown[i] = false;
        }
        runModifierDown = false;
        walkSlowModifierDown = false;
        if (joystickTouchActive) {
            ControlService.sendTouchUp(PTR_JOYSTICK, lastJoystickX, lastJoystickY);
            joystickTouchActive = false;
        }
        joystickCenterX = 0;
        joystickCenterY = 0;
        leftButtonHoldActive = false;
        activeLeftButtonEntry = null;
        
        mouseMoveStopTouch();
        ignoreMouseMoveUntilMs = 0L;
        synchronized (GameMappingService.class) {
            pendingMouseDx = 0f;
            pendingMouseDy = 0f;
        }
        
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
                if (wasdDown[wasdIdx]) {
                    return;
                }
                // 防止 release 丢失导致对向键粘连（典型表现：始终偏向某一方向）
                clearOppositeWasdState(wasdIdx);
                wasdDown[wasdIdx] = true;
                updateJoystickTouch(joy);
                return;
            }
        }

        MappingEntry runMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.RUN);
        if (runMapping != null && runMapping.getTriggerType() == TriggerType.KEYBOARD && runMapping.getKeyCode() == keyCode) {
            if (runModifierDown) {
                return;
            }
            runModifierDown = true;
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                updateJoystickTouch(joy);
            }
            return;
        }

        MappingEntry walkSlowMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.WALK_SLOW);
        if (walkSlowMapping != null
                && walkSlowMapping.getTriggerType() == TriggerType.KEYBOARD
                && walkSlowMapping.getKeyCode() == keyCode) {
            if (walkSlowModifierDown) {
                return;
            }
            walkSlowModifierDown = true;
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                updateJoystickTouch(joy);
            }
            return;
        }

        MappingEntry entry = GameMappingConfig.findMappingByKeyCode(keyCode);
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        if (entry.getTriggerType() == TriggerType.KEYBOARD
                && entry.getType() == MappingType.CLICK
                && entry.getKeyboardPressMode() == GameMappingConfig.KeyboardPressMode.HOLD) {
            // 参考 QtScrcpy：按下即 down，松开即 up；若此前 release 丢失，自动纠正状态
            if (keyboardHoldActive.containsKey(keyCode)) {
                return;
            }
            long pointerId = pointerIdForKeyboardHold(entry);
            int x = clampToScreenX(entry.getPhoneX());
            int y = clampToScreenY(entry.getPhoneY());
            ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            keyboardHoldActive.put(keyCode, entry);
            nonRepeatKeysDown.add(keyCode);
            // Logger.info("game mapping: triggered - " + entry.getName() + " (键盘长按开始)");
            return;
        }

        if (nonRepeatKeysDown.contains(keyCode)) {
            return;
        }
        nonRepeatKeysDown.add(keyCode);

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
                if (!wasdDown[wasdIdx]) {
                    return;
                }
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

        boolean changed = false;
        MappingEntry runMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.RUN);
        if (runMapping != null && runMapping.getTriggerType() == TriggerType.KEYBOARD && runMapping.getKeyCode() == keyCode) {
            runModifierDown = false;
            changed = true;
        }
        MappingEntry walkSlowMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.WALK_SLOW);
        if (walkSlowMapping != null
                && walkSlowMapping.getTriggerType() == TriggerType.KEYBOARD
                && walkSlowMapping.getKeyCode() == keyCode) {
            walkSlowModifierDown = false;
            changed = true;
        }
        if (changed) {
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                updateJoystickTouch(joy);
            }
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

    private static void clearOppositeWasdState(int idx) {
        if (idx == 0) { // W
            wasdDown[2] = false; // clear S
        } else if (idx == 2) { // S
            wasdDown[0] = false; // clear W
        } else if (idx == 1) { // A
            wasdDown[3] = false; // clear D
        } else if (idx == 3) { // D
            wasdDown[1] = false; // clear A
        }
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
        float r = resolveJoystickRadius(entry);

        if (!any) {
            if (joystickTouchActive) {
                ControlService.sendTouchUp(PTR_JOYSTICK, lastJoystickX, lastJoystickY);
                joystickTouchActive = false;
            }
            return;
        }

        int centerX = (int) (cx * sw);
        int centerY = (int) (cy * sh);
        centerX = Math.max(0, Math.min(centerX, sw - 1));
        centerY = Math.max(0, Math.min(centerY, sh - 1));

        int px = (int) (centerX + dx * r * minSide);
        int py = (int) (centerY + dy * r * minSide);
        px = Math.max(0, Math.min(px, sw - 1));
        py = Math.max(0, Math.min(py, sh - 1));

        if (!joystickTouchActive) {
            // 先按住摇杆中心，再拖拽到目标点，确保被游戏识别为“拖动摇杆”
            joystickCenterX = centerX;
            joystickCenterY = centerY;
            ControlService.sendTouchDown(PTR_JOYSTICK, joystickCenterX, joystickCenterY, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            if (px != joystickCenterX || py != joystickCenterY) {
                ControlService.sendTouchMove(PTR_JOYSTICK, px, py);
            }
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

        // 快速移动时先累积增量，再由固定频率发送线程统一下发，避免突发洪峰导致卡顿
        synchronized (GameMappingService.class) {
            pendingMouseDx += deltaX;
            pendingMouseDy += deltaY;
        }
        ensureMouseMovePump();
    }

    public static void ignoreNextMouseMoves(int count) {
        if (count <= 0) {
            return;
        }
        long until = System.currentTimeMillis() + (long) count * mouseMoveIgnoreBaseMs;
        ignoreMouseMoveUntilMs = Math.max(ignoreMouseMoveUntilMs, until);
        synchronized (GameMappingService.class) {
            pendingMouseDx = 0f;
            pendingMouseDy = 0f;
        }
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
        synchronized (GameMappingService.class) {
            pendingMouseDx = 0f;
            pendingMouseDy = 0f;
        }

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
        synchronized (GameMappingService.class) {
            pendingMouseDx = 0f;
            pendingMouseDy = 0f;
        }

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

    private static void ensureMouseMovePump() {
        ensureMouseMoveScheduler();
        if (mouseMovePumpFuture != null && !mouseMovePumpFuture.isDone()) {
            return;
        }
        mouseMovePumpFuture = mouseMoveScheduler.scheduleAtFixedRate(() -> {
            pumpMouseMove();
        }, 0L, MOUSE_MOVE_PUMP_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private static void pumpMouseMove() {
        if (!GameMappingConfig.isMappingMode() || !ControlService.isConnected()) {
            return;
        }
        MappingEntry entry = GameMappingConfig.getMouseMoveMapping();
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < ignoreMouseMoveUntilMs) {
            synchronized (GameMappingService.class) {
                pendingMouseDx = 0f;
                pendingMouseDy = 0f;
            }
            return;
        }

        float rawDx;
        float rawDy;
        synchronized (GameMappingService.class) {
            rawDx = pendingMouseDx;
            rawDy = pendingMouseDy;
            pendingMouseDx = 0f;
            pendingMouseDy = 0f;
        }
        if (rawDx == 0f && rawDy == 0f) {
            return;
        }

        int sensitivity = Math.max(1, entry.getMouseSensitivity());
        float speedRatio = resolveMouseMoveSpeedRatio(sensitivity);

        if (!mouseMoveTouching) {
            mouseMoveStartTouch();
        }
        resetMouseMoveTimeout();

        rawDx = Math.max(-mouseDeltaMaxPx, Math.min(mouseDeltaMaxPx, rawDx));
        rawDy = Math.max(-mouseDeltaMaxPx, Math.min(mouseDeltaMaxPx, rawDy));
        if (Math.abs(rawDx) < mouseDeadzonePx) {
            rawDx = 0f;
        }
        if (Math.abs(rawDy) < mouseDeadzonePx) {
            rawDy = 0f;
        }
        if (rawDx == 0f && rawDy == 0f) {
            return;
        }

        applyMouseDeltaWithSubSteps(rawDx, rawDy, speedRatio);
    }

    private static float resolveMouseMoveSpeedRatio(int sensitivity) {
        // 数值越小越灵敏（更快），数值越大越迟缓（更慢）
        // 这里保持“灵敏度数值越大转得越快”的用户认知：
        // 先把 sensitivity 映射为增益，再转换为 speedRatio（取倒数关系）
        if (sensitivity <= 1) {
            return 8.33f; // ~= 1 / 0.12
        }
        if (sensitivity <= 10) {
            float gain = MOUSE_SENSITIVITY_MIN_GAIN + (sensitivity - 1) * 0.08f;
            return 1f / Math.max(0.08f, gain);
        }
        float gain = 0.84f + (sensitivity - 10) * 0.18f;
        return 1f / Math.max(0.08f, gain);
    }

    private static void applyMouseDeltaWithSubSteps(float rawDx, float rawDy, float speedRatio) {
        float totalDxNorm = (rawDx / speedRatio) / currentVideoWidth;
        float totalDyNorm = (rawDy / speedRatio) / currentVideoHeight;
        float maxNorm = Math.max(Math.abs(totalDxNorm), Math.abs(totalDyNorm));
        int steps = (int) Math.ceil(maxNorm / MOUSE_VIEW_STEP_MAX_NORM);
        steps = Math.max(1, Math.min(MOUSE_VIEW_MAX_STEPS_PER_TICK, steps));

        float stepDx = totalDxNorm / steps;
        float stepDy = totalDyNorm / steps;
        for (int i = 0; i < steps; i++) {
            float nextX = mouseMoveX + stepDx;
            float nextY = mouseMoveY + stepDy;
            if (nextX < VIEW_TOUCH_MIN || nextX > VIEW_TOUCH_MAX || nextY < VIEW_TOUCH_MIN || nextY > VIEW_TOUCH_MAX) {
                // 高速撞边时立即重建触点继续，避免 stop+ignore 导致“转不动”
                mouseMoveStopTouch();
                mouseMoveStartTouch();
                nextX = mouseMoveX + stepDx;
                nextY = mouseMoveY + stepDy;
                if (nextX < VIEW_TOUCH_MIN || nextX > VIEW_TOUCH_MAX || nextY < VIEW_TOUCH_MIN || nextY > VIEW_TOUCH_MAX) {
                    nextX = Math.max(VIEW_TOUCH_MIN, Math.min(VIEW_TOUCH_MAX, nextX));
                    nextY = Math.max(VIEW_TOUCH_MIN, Math.min(VIEW_TOUCH_MAX, nextY));
                }
            }

            mouseMoveX = nextX;
            mouseMoveY = nextY;
            int screenX = (int) (mouseMoveX * currentVideoWidth);
            int screenY = (int) (mouseMoveY * currentVideoHeight);
            ControlService.sendTouchMove(PTR_MOUSE_VIEW, screenX, screenY);
        }
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

    private static float currentJoystickScale() {
        if (walkSlowModifierDown) {
            return JOYSTICK_WALK_SLOW_SCALE;
        }
        if (runModifierDown) {
            return JOYSTICK_RUN_SCALE;
        }
        return JOYSTICK_NORMAL_SCALE;
    }

    private static float resolveJoystickRadius(MappingEntry joystickEntry) {
        if (joystickEntry == null) {
            return clampRadius(0.12f * JOYSTICK_NORMAL_SCALE);
        }
        float base = joystickEntry.getJoystickRadius();
        if (base <= 0f) {
            base = 0.12f;
        }

        if (walkSlowModifierDown) {
            MappingEntry walk = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.WALK_SLOW);
            float custom = radiusByTarget(joystickEntry, walk);
            if (custom > 0f) {
                return clampRadius(custom);
            }
            return clampRadius(base * JOYSTICK_WALK_SLOW_SCALE);
        }
        if (runModifierDown) {
            MappingEntry run = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.RUN);
            float custom = radiusByTarget(joystickEntry, run);
            if (custom > 0f) {
                return clampRadius(custom);
            }
            return clampRadius(base * JOYSTICK_RUN_SCALE);
        }
        return clampRadius(base * JOYSTICK_NORMAL_SCALE);
    }

    private static float radiusByTarget(MappingEntry joystickEntry, MappingEntry targetEntry) {
        if (joystickEntry == null || targetEntry == null || !targetEntry.isEnabled()) {
            return -1f;
        }
        float dx = targetEntry.getPhoneX() - joystickEntry.getPhoneX();
        float dy = targetEntry.getPhoneY() - joystickEntry.getPhoneY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist > 0f ? dist : -1f;
    }

    private static float clampRadius(float r) {
        return Math.max(JOYSTICK_RADIUS_MIN, Math.min(JOYSTICK_RADIUS_MAX, r));
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
