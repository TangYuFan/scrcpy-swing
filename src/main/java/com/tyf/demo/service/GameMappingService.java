package com.tyf.demo.service;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;
import org.pmw.tinylog.Logger;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameMappingService {

    private static final long LEFT_LONG_PRESS_MS = 280;
    private static final long MOUSE_MOVE_TIMEOUT_MS = 500;

    private static int currentVideoWidth = 1080;
    private static int currentVideoHeight = 1920;

    private static final Set<Integer> nonRepeatKeysDown = new HashSet<>();

    private static final boolean[] wasdDown = new boolean[4];

    private static boolean joystickTouchActive;
    private static int lastJoystickX;
    private static int lastJoystickY;

    private static long leftButtonDownTime;
    private static volatile boolean leftButtonStillDown;
    private static volatile boolean leftLongHoldActive;
    private static ScheduledExecutorService mouseScheduler;
    private static ScheduledFuture<?> longPressFuture;

    // 鼠标移动视角相关状态
    private static boolean mouseMoveTouching = false;
    private static float mouseMoveX = 0.5f;
    private static float mouseMoveY = 0.5f;
    private static int ignoreMouseMoveCount = 0;
    private static ScheduledFuture<?> mouseMoveTimeoutFuture;

    private GameMappingService() {}

    public static void resetState() {
        nonRepeatKeysDown.clear();
        for (int i = 0; i < wasdDown.length; i++) {
            wasdDown[i] = false;
        }
        if (joystickTouchActive) {
            ControlService.sendTouchUp(lastJoystickX, lastJoystickY);
            joystickTouchActive = false;
        }
        leftButtonStillDown = false;
        leftLongHoldActive = false;
        cancelLongPressSchedule();
        
        mouseMoveStopTouch();
        
        Logger.debug("game mapping: state reset");
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
                Logger.info("game mapping: triggered - " + joy.getName() + " (WASD)");
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
                ControlService.sendTouchUp(lastJoystickX, lastJoystickY);
                joystickTouchActive = false;
            }
            return;
        }

        int px = (int) (cx * sw + dx * r * minSide);
        int py = (int) (cy * sh + dy * r * minSide);
        px = Math.max(0, Math.min(px, sw - 1));
        py = Math.max(0, Math.min(py, sh - 1));

        if (!joystickTouchActive) {
            ControlService.sendTouchDown(px, py, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            joystickTouchActive = true;
            lastJoystickX = px;
            lastJoystickY = py;
        } else {
            ControlService.sendTouchMove(px, py);
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

        int sensitivity = entry.getMouseSensitivity();

        if (ignoreMouseMoveCount > 0) {
            ignoreMouseMoveCount--;
            return;
        }

        if (!mouseMoveTouching) {
            mouseMoveStartTouch();
        }

        resetMouseMoveTimeout();

        mouseMoveX += (float) deltaX * sensitivity / currentVideoWidth;
        mouseMoveY += (float) deltaY * sensitivity / currentVideoHeight;

        mouseMoveX = Math.max(0.05f, Math.min(0.95f, mouseMoveX));
        mouseMoveY = Math.max(0.05f, Math.min(0.95f, mouseMoveY));

        int screenX = (int) (mouseMoveX * currentVideoWidth);
        int screenY = (int) (mouseMoveY * currentVideoHeight);

        ControlService.sendTouchMove(screenX, screenY);
        Logger.debug("game mapping: view moved delta=(" + deltaX + "," + deltaY + ") -> pos=(" + mouseMoveX + "," + mouseMoveY + ")");
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

        ControlService.sendTouchDown(screenX, screenY, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        mouseMoveTouching = true;

        Logger.debug("game mapping: view touch start at (" + startX + "," + startY + ")");
    }

    private static void mouseMoveStopTouch() {
        if (!mouseMoveTouching) {
            return;
        }

        int screenX = (int) (mouseMoveX * currentVideoWidth);
        int screenY = (int) (mouseMoveY * currentVideoHeight);

        ControlService.sendTouchUp(screenX, screenY);
        mouseMoveTouching = false;
        cancelMouseMoveTimeout();

        Logger.debug("game mapping: view touch stop");
    }

    private static void ensureMouseScheduler() {
        if (mouseScheduler == null || mouseScheduler.isShutdown()) {
            mouseScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "game-mapping-mouse");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private static void resetMouseMoveTimeout() {
        cancelMouseMoveTimeout();
        ensureMouseScheduler();
        mouseMoveTimeoutFuture = mouseScheduler.schedule(() -> {
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
                Logger.info("game mapping: triggered - " + aim.getName());
                executeClickDown(aim.getPhoneX(), aim.getPhoneY());
            }
            return;
        }

        if (button != MouseEvent.BUTTON1) {
            return;
        }

        MappingEntry tap = GameMappingConfig.findMouseLeftTapMapping();
        MappingEntry lng = GameMappingConfig.findMouseLeftLongMapping();

        leftButtonDownTime = System.currentTimeMillis();
        leftButtonStillDown = true;
        leftLongHoldActive = false;
        cancelLongPressSchedule();

        // 长按模式：按下时立即 touch down，释放时 touch up
        if (lng != null && lng.isEnabled()) {
            leftLongHoldActive = true;
            Logger.info("game mapping: triggered - " + lng.getName() + " (按下)");
            executeClickDown(lng.getPhoneX(), lng.getPhoneY());
        }

        // 点按模式：按下时立即 touch down + touch up
        if (tap != null && tap.isEnabled()) {
            Logger.info("game mapping: triggered - " + tap.getName() + " (点按)");
            executeMapping(tap);
        }
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
                Logger.info("game mapping: triggered - " + aim.getName() + " (释放)");
                ControlService.sendTouchUp(x, y);
            }
            return;
        }

        if (button != MouseEvent.BUTTON1) {
            return;
        }

        leftButtonStillDown = false;
        cancelLongPressSchedule();

        MappingEntry lng = GameMappingConfig.findMouseLeftLongMapping();

        if (leftLongHoldActive) {
            leftLongHoldActive = false;
            if (lng != null && lng.isEnabled()) {
                Logger.info("game mapping: triggered - " + lng.getName() + " (释放)");
                int x = (int) (currentVideoWidth * lng.getPhoneX());
                int y = (int) (currentVideoHeight * lng.getPhoneY());
                ControlService.sendTouchUp(x, y);
            }
        }
    }

    private static void cancelLongPressSchedule() {
        if (longPressFuture != null) {
            longPressFuture.cancel(false);
            longPressFuture = null;
        }
    }

    private static void executeClickDown(float ratioX, float ratioY) {
        int x = (int) (currentVideoWidth * ratioX);
        int y = (int) (currentVideoHeight * ratioY);
        x = Math.max(0, Math.min(x, currentVideoWidth - 1));
        y = Math.max(0, Math.min(y, currentVideoHeight - 1));
        ControlService.sendTouchDown(x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        Logger.debug("game mapping: touch down at (" + ratioX + ", " + ratioY + ")");
    }

    private static void executeMapping(MappingEntry entry) {
        if (entry == null) {
            return;
        }

        Logger.info("game mapping: triggered - " + entry.getName());

        switch (entry.getType()) {
            case CLICK:
                if (entry.getTriggerType() == TriggerType.MOUSE_RIGHT) {
                    return;
                }
                executeClick(entry.getPhoneX(), entry.getPhoneY());
                break;
            case MOUSE_MOVE:
            case JOYSTICK_WASD:
                break;
            default:
                Logger.warn("game mapping: unsupported type " + entry.getType());
                break;
        }
    }

    private static void executeClick(float ratioX, float ratioY) {
        int x = (int) (currentVideoWidth * ratioX);
        int y = (int) (currentVideoHeight * ratioY);
        x = Math.max(0, Math.min(x, currentVideoWidth - 1));
        y = Math.max(0, Math.min(y, currentVideoHeight - 1));

        ControlService.sendTouchDown(x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        ControlService.sendTouchUp(x, y);

        Logger.debug("game mapping: click at (" + ratioX + ", " + ratioY + ")");
    }
}
