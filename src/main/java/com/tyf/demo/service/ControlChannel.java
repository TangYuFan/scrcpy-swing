package com.tyf.demo.service;

import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 *   @desc : Scrcpy 控制通道，负责Socket消息序列化
 *   @auth : tyf
 *   @date : 2026-03-20
 */
final class ControlChannel {

    private final OutputStream out;

    ControlChannel(OutputStream out) {
        this.out = out;
    }

    public void send(ControlMessage msg) throws IOException {
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
            default:
                writeEmpty(msg.getType());
                break;
        }
    }

    // TYPE_INJECT_KEYCODE: type(1) + action(4) + keycode(4) + repeat(4) + metaState(4) = 17 bytes
    private void writeInjectKeycode(ControlMessage msg) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(17);
        buf.put((byte) ControlMessage.TYPE_INJECT_KEYCODE);
        buf.putInt(msg.getAction());
        buf.putInt(msg.getKeycode());
        buf.putInt(msg.getRepeat());
        buf.putInt(msg.getMetaState());
        out.write(buf.array());
        out.flush();
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
        byte[] textBytes = text.getBytes("UTF-8");
        ByteBuffer buf = ByteBuffer.allocate(5 + textBytes.length);
        buf.put((byte) ControlMessage.TYPE_INJECT_TEXT);
        buf.putInt(textBytes.length);
        buf.put(textBytes);
        out.write(buf.array());
        out.flush();
    }

    // TYPE_INJECT_TOUCH_EVENT: type(1) + action(4) + pointerId(8) + x(4) + y(4) + screenW(4) + screenH(4) + pressure(4) = 33 bytes
    private void writeInjectTouchEvent(ControlMessage msg) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(33);
        buf.put((byte) ControlMessage.TYPE_INJECT_TOUCH_EVENT);
        buf.putInt(msg.getAction());
        buf.putLong(msg.getPointerId());
        buf.putInt(msg.getPositionX());
        buf.putInt(msg.getPositionY());
        buf.putInt(msg.getScreenWidth());
        buf.putInt(msg.getScreenHeight());
        buf.putFloat(msg.getPressure());
        out.write(buf.array());
        out.flush();
    }

    // TYPE_INJECT_SCROLL_EVENT: type(1) + x(4) + y(4) + screenW(4) + screenH(4) + hScroll(4) + vScroll(4) = 25 bytes
    private void writeInjectScrollEvent(ControlMessage msg) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(25);
        buf.put((byte) ControlMessage.TYPE_INJECT_SCROLL_EVENT);
        buf.putInt(msg.getPositionX());
        buf.putInt(msg.getPositionY());
        buf.putInt(msg.getScreenWidth());
        buf.putInt(msg.getScreenHeight());
        buf.putFloat(msg.getHScroll());
        buf.putFloat(msg.getVScroll());
        out.write(buf.array());
        out.flush();
    }

    // TYPE_BACK_OR_SCREEN_ON: type(1) + action(4) = 5 bytes
    private void writeBackOrScreenOn(ControlMessage msg) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put((byte) ControlMessage.TYPE_BACK_OR_SCREEN_ON);
        buf.putInt(msg.getAction());
        out.write(buf.array());
        out.flush();
    }

    // 空消息: type(1) = 1 byte
    private void writeEmpty(int type) throws IOException {
        out.write(type);
        out.flush();
    }
}
