package com.tyf.demo.service;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 *   @desc : Scrcpy 控制通道，负责Socket消息序列化
 *   @auth : tyf
 *   @date : 2026-03-20
 */
final class ControlChannel {

    private final DataOutputStream out;
    private static final int OUT_BUFFER_SIZE = 8192;
    private static final long TOUCH_FLUSH_INTERVAL_NS = 2_000_000L; // 2ms
    private static final int TOUCH_FLUSH_MAX_PENDING = 6;
    private int pendingTouchWrites = 0;
    private long lastFlushNs = System.nanoTime();

    ControlChannel(OutputStream out) {
        this.out = new DataOutputStream(new BufferedOutputStream(out, OUT_BUFFER_SIZE));
    }

    public synchronized void send(ControlMessage msg) throws IOException {
        switch (msg.getType()) {
            case ControlMessage.TYPE_INJECT_KEYCODE:
                writeInjectKeycode(msg);
                break;
            case ControlMessage.TYPE_INJECT_TEXT:
                writeInjectText(msg);
                break;
            case ControlMessage.TYPE_INJECT_TOUCH_EVENT:
                writeInjectTouchEvent(msg);
                break;
            case ControlMessage.TYPE_INJECT_SCROLL_EVENT:
                writeInjectScrollEvent(msg);
                break;
            case ControlMessage.TYPE_BACK_OR_SCREEN_ON:
                writeBackOrScreenOn(msg);
                break;
            case ControlMessage.TYPE_INJECT_MOUSE_MOVE_EVENT:
                writeInjectMouseMoveEvent(msg);
                break;
            default:
                writeEmpty(msg.getType());
                break;
        }
    }

    private static String getTypeName(int type) {
        switch (type) {
            case ControlMessage.TYPE_INJECT_KEYCODE: return "KEYCODE";
            case ControlMessage.TYPE_INJECT_TEXT: return "TEXT";
            case ControlMessage.TYPE_INJECT_TOUCH_EVENT: return "TOUCH";
            case ControlMessage.TYPE_INJECT_SCROLL_EVENT: return "SCROLL";
            case ControlMessage.TYPE_BACK_OR_SCREEN_ON: return "BACK_OR_SCREEN_ON";
            case ControlMessage.TYPE_EXPAND_NOTIFICATION_PANEL: return "EXPAND_NOTIFICATION";
            case ControlMessage.TYPE_COLLAPSE_PANELS: return "COLLAPSE_PANELS";
            default: return "UNKNOWN(" + type + ")";
        }
    }

    /**
     *   @desc : 写入一个字节
     */
    private void writeByte(int v) throws IOException {
        out.write(v);
    }

    /**
     *   @desc : 写入 32 位整数（大端序）
     */
    private void writeInt(int v) throws IOException {
        out.writeInt(v);
    }

    /**
     *   @desc : 写入 16 位无符号整数（大端序）
     */
    private void writeShort(int v) throws IOException {
        out.writeShort(v & 0xFFFF);
    }

    // TYPE_INJECT_KEYCODE: type(1) + action(1) + keycode(4) + repeat(4) + metaState(4) = 14 bytes
    private void writeInjectKeycode(ControlMessage msg) throws IOException {
        writeByte(ControlMessage.TYPE_INJECT_KEYCODE);
        writeByte(msg.getAction());
        writeInt(msg.getKeycode());
        writeInt(msg.getRepeat());
        writeInt(msg.getMetaState());
        flushNow();
    }

    // TYPE_INJECT_TEXT: type(1) + textLength(4) + text(n) = 5 + n bytes
    private void writeInjectText(ControlMessage msg) throws IOException {
        String text = msg.getText();
        if (text == null) {
            text = "";
        }
        if (text.length() > ControlMessage.INJECT_TEXT_MAX_LENGTH) {
            text = text.substring(0, ControlMessage.INJECT_TEXT_MAX_LENGTH);
        }
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        writeByte(ControlMessage.TYPE_INJECT_TEXT);
        writeInt(textBytes.length);
        out.write(textBytes);
        flushNow();
    }

    // TYPE_INJECT_TOUCH_EVENT: 
    // type(1) + action(1) + pointerId(8) + x(4) + y(4) + screenWidth(2) + screenHeight(2) + pressure(2) + actionButton(4) + buttons(4) = 32 bytes
    private void writeInjectTouchEvent(ControlMessage msg) throws IOException {
        // 打印字节数组
        byte[] data = new byte[32];
        int pos = 0;
        data[pos++] = (byte) ControlMessage.TYPE_INJECT_TOUCH_EVENT;
        data[pos++] = (byte) msg.getAction();
        // pointerId (8 bytes big endian)
        for (int i = 7; i >= 0; i--) {
            data[pos++] = (byte) (msg.getPointerId() >> (i * 8));
        }
        // x (4 bytes big endian)
        for (int i = 3; i >= 0; i--) {
            data[pos++] = (byte) (msg.getPositionX() >> (i * 8));
        }
        // y (4 bytes big endian)
        for (int i = 3; i >= 0; i--) {
            data[pos++] = (byte) (msg.getPositionY() >> (i * 8));
        }
        // screenWidth (2 bytes big endian)
        data[pos++] = (byte) (msg.getScreenWidth() >> 8);
        data[pos++] = (byte) msg.getScreenWidth();
        // screenHeight (2 bytes big endian)
        data[pos++] = (byte) (msg.getScreenHeight() >> 8);
        data[pos++] = (byte) msg.getScreenHeight();
        // pressure (2 bytes big endian)
        int pressure = msg.getPressureInt();
        data[pos++] = (byte) (pressure >> 8);
        data[pos++] = (byte) pressure;
        // actionButton (4 bytes big endian)
        for (int i = 3; i >= 0; i--) {
            data[pos++] = (byte) (msg.getActionButton() >> (i * 8));
        }
        // buttons (4 bytes big endian)
        for (int i = 3; i >= 0; i--) {
            data[pos++] = (byte) (msg.getButtons() >> (i * 8));
        }
        
        // 写入并刷新
        out.write(data);
        // DOWN / UP 必须立即刷出：
        // - DOWN：与紧随 MOVE 的顺序正确
        // - UP：若与 MOVE 一起走 flushForTouchLike，在 2ms/未满 6 条时可能长期留在 BufferedOutputStream，
        //   设备端收不到抬起，表现为“滑屏/按住不松”
        if (msg.getAction() == ControlMessage.ACTION_DOWN_TOUCH
                || msg.getAction() == ControlMessage.ACTION_UP_TOUCH) {
            flushNow();
        } else {
            flushForTouchLike();
        }

        // 打印十六进制（调试用）
        // StringBuilder hex = new StringBuilder();
        // for (byte b : data) {
        //     hex.append(String.format("%02X ", b));
        // }
        // Logger.debug("control: TOUCH hex=[" + hex.toString() + "]");
    }

    // TYPE_INJECT_SCROLL_EVENT: 
    // type(1) + x(4) + y(4) + screenWidth(2) + screenHeight(2) + hScroll(2) + vScroll(2) + buttons(4) = 21 bytes
    private void writeInjectScrollEvent(ControlMessage msg) throws IOException {
        writeByte(ControlMessage.TYPE_INJECT_SCROLL_EVENT);
        writeInt(msg.getPositionX());
        writeInt(msg.getPositionY());
        writeShort(msg.getScreenWidth());
        writeShort(msg.getScreenHeight());
        writeShort(msg.getHScrollInt());   // i16 fixed point
        writeShort(msg.getVScrollInt());   // i16 fixed point
        writeInt(msg.getButtons());
        flushForTouchLike();
    }

    // TYPE_BACK_OR_SCREEN_ON: type(1) + action(1) = 2 bytes
    private void writeBackOrScreenOn(ControlMessage msg) throws IOException {
        writeByte(ControlMessage.TYPE_BACK_OR_SCREEN_ON);
        writeByte(msg.getAction());
        flushNow();
    }

    // TYPE_INJECT_MOUSE_MOVE_EVENT: type(1) + motionX(4) + motionY(4) = 9 bytes
    private void writeInjectMouseMoveEvent(ControlMessage msg) throws IOException {
        writeByte(ControlMessage.TYPE_INJECT_MOUSE_MOVE_EVENT);
        writeInt(msg.getMotionEventX());
        writeInt(msg.getMotionEventY());
        flushNow();
    }
    
    // 空消息: type(1) = 1 byte
    private void writeEmpty(int type) throws IOException {
        out.write(type);
        flushNow();
    }

    private void flushForTouchLike() throws IOException {
        pendingTouchWrites++;
        long nowNs = System.nanoTime();
        if (pendingTouchWrites >= TOUCH_FLUSH_MAX_PENDING || (nowNs - lastFlushNs) >= TOUCH_FLUSH_INTERVAL_NS) {
            flushNow();
        }
    }

    private void flushNow() throws IOException {
        out.flush();
        pendingTouchWrites = 0;
        lastFlushNs = System.nanoTime();
    }

    /** 供摇杆等场景在 DOWN+首帧 MOVE 后强制刷出，避免 MOVE 滞留在缓冲里 */
    void flushOutputNow() throws IOException {
        flushNow();
    }
}
