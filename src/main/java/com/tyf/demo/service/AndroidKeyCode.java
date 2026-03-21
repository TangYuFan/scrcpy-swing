package com.tyf.demo.service;

/**
 *   @desc : Android KeyEvent 按键码定义
 *   @auth : tyf
 *   @date : 2026-03-20
 */
public final class AndroidKeyCode {

    // 按键码
    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_HOME = 3;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_CALL = 5;
    public static final int KEYCODE_ENDCALL = 6;
    public static final int KEYCODE_0 = 7;
    public static final int KEYCODE_1 = 8;
    public static final int KEYCODE_2 = 9;
    public static final int KEYCODE_3 = 10;
    public static final int KEYCODE_4 = 11;
    public static final int KEYCODE_5 = 12;
    public static final int KEYCODE_6 = 13;
    public static final int KEYCODE_7 = 14;
    public static final int KEYCODE_8 = 15;
    public static final int KEYCODE_9 = 16;
    public static final int KEYCODE_STAR = 17;
    public static final int KEYCODE_POUND = 18;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_POWER = 26;
    public static final int KEYCODE_CAMERA = 27;
    public static final int KEYCODE_CLEAR = 28;
    public static final int KEYCODE_A = 29;
    public static final int KEYCODE_B = 30;
    public static final int KEYCODE_C = 31;
    public static final int KEYCODE_D = 32;
    public static final int KEYCODE_E = 33;
    public static final int KEYCODE_F = 34;
    public static final int KEYCODE_G = 35;
    public static final int KEYCODE_H = 36;
    public static final int KEYCODE_I = 37;
    public static final int KEYCODE_J = 38;
    public static final int KEYCODE_K = 39;
    public static final int KEYCODE_L = 40;
    public static final int KEYCODE_M = 41;
    public static final int KEYCODE_N = 42;
    public static final int KEYCODE_O = 43;
    public static final int KEYCODE_P = 44;
    public static final int KEYCODE_Q = 45;
    public static final int KEYCODE_R = 46;
    public static final int KEYCODE_S = 47;
    public static final int KEYCODE_T = 48;
    public static final int KEYCODE_U = 49;
    public static final int KEYCODE_V = 50;
    public static final int KEYCODE_W = 51;
    public static final int KEYCODE_X = 52;
    public static final int KEYCODE_Y = 53;
    public static final int KEYCODE_Z = 54;
    public static final int KEYCODE_COMMA = 55;
    public static final int KEYCODE_PERIOD = 56;
    public static final int KEYCODE_ALT_LEFT = 57;
    public static final int KEYCODE_ALT_RIGHT = 58;
    public static final int KEYCODE_SHIFT_LEFT = 59;
    public static final int KEYCODE_SHIFT_RIGHT = 60;
    public static final int KEYCODE_TAB = 61;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_VOLUME_MUTE = 164;

    // Meta 状态
    public static final int META_NONE = 0;
    public static final int META_SHIFT_ON = 1;
    public static final int META_ALT_ON = 2;
    public static final int META_CTRL_ON = 4096;
    public static final int META_CAPS_LOCK_ON = 1048576;
    public static final int META_NUM_LOCK_ON = 2097152;

    private AndroidKeyCode() {}

    public static int getKeyCode(String keyName) {
        switch (keyName.toUpperCase()) {
            case "HOME": return KEYCODE_HOME;
            case "BACK": return KEYCODE_BACK;
            case "ENTER": return KEYCODE_ENTER;
            case "SPACE": return KEYCODE_SPACE;
            case "TAB": return KEYCODE_TAB;
            case "DELETE": case "DEL": return KEYCODE_DEL;
            case "POWER": return KEYCODE_POWER;
            case "MENU": return KEYCODE_MENU;
            case "VOLUME_UP": return KEYCODE_VOLUME_UP;
            case "VOLUME_DOWN": return KEYCODE_VOLUME_DOWN;
            case "VOLUME_MUTE": return KEYCODE_VOLUME_MUTE;
            case "CAMERA": return KEYCODE_CAMERA;
            case "UP": case "ARROW_UP": return KEYCODE_DPAD_UP;
            case "DOWN": case "ARROW_DOWN": return KEYCODE_DPAD_DOWN;
            case "LEFT": case "ARROW_LEFT": return KEYCODE_DPAD_LEFT;
            case "RIGHT": case "ARROW_RIGHT": return KEYCODE_DPAD_RIGHT;
            case "CENTER": case "OK": return KEYCODE_DPAD_CENTER;
            case "ESCAPE": case "ESC": return KEYCODE_BACK;
            default:
                if (keyName.length() == 1) {
                    char c = Character.toUpperCase(keyName.charAt(0));
                    if (c >= 'A' && c <= 'Z') {
                        return KEYCODE_A + (c - 'A');
                    }
                    if (c >= '0' && c <= '9') {
                        return KEYCODE_0 + (c - '0');
                    }
                }
                return KEYCODE_UNKNOWN;
        }
    }

    public static final int KEYCODE_MENU = 82;
}
