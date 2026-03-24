package com.tyf.demo.service.mapping;

import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * @desc : 键位字符串与 KeyEvent 键码互转（用于映射配置）
 * @auth : tyf
 * @date : 2026-03-20
 */
public final class KeyMappingUtil {

    private KeyMappingUtil() {}

    public static int parseKeyString(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        String t = s.trim().toUpperCase(Locale.ROOT);
        // 统一去除常见分隔符，兼容 "LEFT SHIFT" / "caps-lock" / "caps_lock"
        String n = t.replace(" ", "").replace("-", "").replace("_", "");
        switch (t) {
            case "SPACE":
            case "空格":
                return KeyEvent.VK_SPACE;
            case "ENTER":
            case "回车":
                return KeyEvent.VK_ENTER;
            case "TAB":
                return KeyEvent.VK_TAB;
            case "ESC":
            case "ESCAPE":
                return KeyEvent.VK_ESCAPE;
            case "SHIFT":
                return KeyEvent.VK_SHIFT;
            case "CTRL":
            case "CONTROL":
                return KeyEvent.VK_CONTROL;
            case "ALT":
                return KeyEvent.VK_ALT;
            case "CAPS":
            case "CAPSLOCK":
            case "CAPS_LOCK":
                return KeyEvent.VK_CAPS_LOCK;
            default:
                break;
        }
        switch (n) {
            case "LEFTSHIFT":
            case "RIGHTSHIFT":
            case "LSHIFT":
            case "RSHIFT":
            case "SHIFT":
                return KeyEvent.VK_SHIFT;
            case "LEFTCTRL":
            case "RIGHTCTRL":
            case "LCTRL":
            case "RCTRL":
            case "CONTROL":
            case "CTRL":
                return KeyEvent.VK_CONTROL;
            case "LEFTALT":
            case "RIGHTALT":
            case "LALT":
            case "RALT":
            case "ALT":
                return KeyEvent.VK_ALT;
            case "CAPSLOCK":
            case "CAPS":
                return KeyEvent.VK_CAPS_LOCK;
            default:
                break;
        }
        if (t.length() == 1) {
            char c = t.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return KeyEvent.VK_A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return KeyEvent.VK_0 + (c - '0');
            }
        }
        if (t.startsWith("F") && t.length() <= 3) {
            try {
                int fn = Integer.parseInt(t.substring(1));
                if (fn >= 1 && fn <= 12) {
                    return KeyEvent.VK_F1 + (fn - 1);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            // 兜底：支持直接输入 VK_XXX / XXX（例如 VK_SHIFT）
            String symbol = n.startsWith("VK") ? n.substring(2) : n;
            try {
                java.lang.reflect.Field f = KeyEvent.class.getField("VK_" + symbol);
                return f.getInt(null);
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    public static String keyCodeToDisplay(int keyCode) {
        if (keyCode == 0) {
            return "";
        }
        if (keyCode == KeyEvent.VK_SPACE) {
            return "SPACE";
        }
        if (keyCode == KeyEvent.VK_ENTER) {
            return "ENTER";
        }
        if (keyCode == KeyEvent.VK_TAB) {
            return "TAB";
        }
        if (keyCode == KeyEvent.VK_ESCAPE) {
            return "ESC";
        }
        if (keyCode == KeyEvent.VK_SHIFT) {
            return "SHIFT";
        }
        if (keyCode == KeyEvent.VK_CONTROL) {
            return "CTRL";
        }
        if (keyCode == KeyEvent.VK_ALT) {
            return "ALT";
        }
        if (keyCode == KeyEvent.VK_CAPS_LOCK) {
            return "CAPSLOCK";
        }
        if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) {
            return "F" + (1 + (keyCode - KeyEvent.VK_F1));
        }
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
            return String.valueOf((char) ('A' + (keyCode - KeyEvent.VK_A)));
        }
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            return String.valueOf((char) ('0' + (keyCode - KeyEvent.VK_0)));
        }
        return String.valueOf(keyCode);
    }
}
