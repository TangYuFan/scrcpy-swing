package com.tyf.demo.service;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.TriggerType;
import org.pmw.tinylog.Logger;

public class GameMappingService {

    private static int currentVideoWidth = 1080;
    private static int currentVideoHeight = 1920;

    private GameMappingService() {}

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

        MappingEntry entry = GameMappingConfig.findMappingByKeyCode(keyCode);
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        executeMapping(entry);
    }

    public static void handleKeyReleased(int keyCode) {
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
        int screenX = currentVideoWidth / 2 + deltaX * sensitivity;
        int screenY = currentVideoHeight / 2 + deltaY * sensitivity;

        screenX = Math.max(0, Math.min(screenX, currentVideoWidth - 1));
        screenY = Math.max(0, Math.min(screenY, currentVideoHeight - 1));

        ControlService.sendTouchMove(screenX, screenY);
    }

    public static void handleMousePressed(int button) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        MappingEntry entry = GameMappingConfig.findMappingByMouseButton(button);
        if (entry != null && entry.isEnabled()) {
            executeMapping(entry);
        }
    }

    public static void handleMouseReleased(int button) {
    }

    private static void executeMapping(MappingEntry entry) {
        if (entry == null) return;

        switch (entry.getType()) {
            case CLICK:
                executeClick(entry.getPhoneX(), entry.getPhoneY());
                break;
            default:
                Logger.warn("game mapping: unsupported type " + entry.getType());
                break;
        }
    }

    private static void executeClick(float ratioX, float ratioY) {
        int x = (int) (currentVideoWidth * ratioX);
        int y = (int) (currentVideoHeight * ratioY);

        ControlService.sendTouchDown(x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        ControlService.sendTouchUp(x, y);

        Logger.debug("game mapping: click at (" + ratioX + ", " + ratioY + ")");
    }
}
