package com.tyf.demo.service;

/**
 *   @desc : Scrcpy 控制消息类型定义
 *   @auth : tyf
 *   @date : 2026-03-20
 */
public final class ControlMessage {

    // 消息类型
    public static final int TYPE_INJECT_KEYCODE = 0;
    public static final int TYPE_INJECT_TEXT = 1;
    public static final int TYPE_INJECT_TOUCH_EVENT = 2;
    public static final int TYPE_INJECT_SCROLL_EVENT = 3;
    public static final int TYPE_BACK_OR_SCREEN_ON = 4;
    public static final int TYPE_EXPAND_NOTIFICATION_PANEL = 5;
    public static final int TYPE_EXPAND_SETTINGS_PANEL = 6;
    public static final int TYPE_COLLAPSE_PANELS = 7;
    public static final int TYPE_GET_CLIPBOARD = 8;
    public static final int TYPE_SET_CLIPBOARD = 9;
    public static final int TYPE_SET_DISPLAY_POWER = 10;
    public static final int TYPE_ROTATE_DEVICE = 11;

    // 按键动作
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;

    // 触摸动作
    public static final int ACTION_DOWN_TOUCH = 0;
    public static final int ACTION_UP_TOUCH = 1;
    public static final int ACTION_MOVE_TOUCH = 2;

    // 鼠标模拟触摸的指针ID
    public static final long POINTER_ID_MOUSE = -1;

    // 最大文本长度
    public static final int INJECT_TEXT_MAX_LENGTH = 300;

    private int type;
    private int action;
    private int keycode;
    private int repeat;
    private int metaState;
    private String text;
    private long pointerId;
    private float pressure;
    private int positionX;
    private int positionY;
    private int screenWidth;
    private int screenHeight;
    private float hScroll;
    private float vScroll;
    private int buttons;

    private ControlMessage() {}

    public static ControlMessage createInjectKeycode(int action, int keycode, int repeat, int metaState) {
        ControlMessage msg = new ControlMessage();
        msg.type = TYPE_INJECT_KEYCODE;
        msg.action = action;
        msg.keycode = keycode;
        msg.repeat = repeat;
        msg.metaState = metaState;
        return msg;
    }

    public static ControlMessage createInjectText(String text) {
        ControlMessage msg = new ControlMessage();
        msg.type = TYPE_INJECT_TEXT;
        msg.text = text;
        return msg;
    }

    public static ControlMessage createInjectTouchEvent(int action, long pointerId, float x, float y, 
                                                        int screenWidth, int screenHeight, 
                                                        float pressure) {
        ControlMessage msg = new ControlMessage();
        msg.type = TYPE_INJECT_TOUCH_EVENT;
        msg.action = action;
        msg.pointerId = pointerId;
        msg.positionX = (int) x;
        msg.positionY = (int) y;
        msg.screenWidth = screenWidth;
        msg.screenHeight = screenHeight;
        msg.pressure = pressure;
        return msg;
    }

    public static ControlMessage createInjectScrollEvent(int x, int y, int screenWidth, int screenHeight,
                                                          float hScroll, float vScroll) {
        ControlMessage msg = new ControlMessage();
        msg.type = TYPE_INJECT_SCROLL_EVENT;
        msg.positionX = x;
        msg.positionY = y;
        msg.screenWidth = screenWidth;
        msg.screenHeight = screenHeight;
        msg.hScroll = hScroll;
        msg.vScroll = vScroll;
        return msg;
    }

    public static ControlMessage createBackOrScreenOn(int action) {
        ControlMessage msg = new ControlMessage();
        msg.type = TYPE_BACK_OR_SCREEN_ON;
        msg.action = action;
        return msg;
    }

    public static ControlMessage createEmpty(int type) {
        ControlMessage msg = new ControlMessage();
        msg.type = type;
        return msg;
    }

    // Getters
    public int getType() { return type; }
    public int getAction() { return action; }
    public int getKeycode() { return keycode; }
    public int getRepeat() { return repeat; }
    public int getMetaState() { return metaState; }
    public String getText() { return text; }
    public long getPointerId() { return pointerId; }
    public float getPressure() { return pressure; }
    public int getPositionX() { return positionX; }
    public int getPositionY() { return positionY; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
    public float getHScroll() { return hScroll; }
    public float getVScroll() { return vScroll; }
    public int getButtons() { return buttons; }
}
