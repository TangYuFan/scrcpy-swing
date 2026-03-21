package com.tyf.demo.service;

import com.tyf.demo.gui.ContentPanel;
import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

/**
 *   @desc : Scrcpy 控制服务，处理触摸、键盘等输入事件
 *   @auth : tyf
 *   @date : 2026-03-20
 */
public final class ControlService {

    private static final int CONTROL_PORT = ConstService.SCRCPY_CONTROL_PORT;

    private static final AtomicReference<Socket> controlSocket = new AtomicReference<>();
    private static final AtomicReference<ControlChannel> controlChannel = new AtomicReference<>();
    private static final AtomicReference<Thread> controlThread = new AtomicReference<>();

    private static volatile int currentVideoWidth;
    private static volatile int currentVideoHeight;
    private static volatile boolean videoRotation = false;

    private ControlService() {}

    public static void startControl(String deviceId) {
        Thread t = new Thread(() -> {
            try {
                connectControlSocket(deviceId);
            } catch (Exception e) {
                Logger.error("control: connect failed - " + e.getMessage());
            }
        }, "scrcpy-control");
        controlThread.set(t);
        t.start();
    }

    private static void connectControlSocket(String deviceId) throws IOException {
        Socket sock = new Socket();
        sock.connect(new java.net.InetSocketAddress("127.0.0.1", CONTROL_PORT), 2000);
        sock.setTcpNoDelay(true);
        controlSocket.set(sock);

        OutputStream out = sock.getOutputStream();
        ControlChannel channel = new ControlChannel(out);
        controlChannel.set(channel);

        Logger.info("control: connected");
    }

    public static void shutdown() {
        Socket sock = controlSocket.getAndSet(null);
        controlChannel.set(null);
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException ignore) {}
        }
        Thread t = controlThread.getAndSet(null);
        if (t != null) {
            try {
                t.join(1000);
            } catch (InterruptedException ignore) {}
        }
        Logger.info("control: shutdown");
    }

    public static void updateVideoSize(int width, int height) {
        currentVideoWidth = width;
        currentVideoHeight = height;
        Logger.debug("control: video size " + width + "x" + height);
    }

    public static void setVideoRotation(boolean rotation) {
        videoRotation = rotation;
    }

    public static void sendTouchDown(int x, int y) {
        sendTouch(ControlMessage.ACTION_DOWN_TOUCH, x, y);
    }

    public static void sendTouchUp(int x, int y) {
        sendTouch(ControlMessage.ACTION_UP_TOUCH, x, y);
    }

    public static void sendTouchMove(int x, int y) {
        sendTouch(ControlMessage.ACTION_MOVE_TOUCH, x, y);
    }

    private static void sendTouch(int action, int x, int y) {
        ControlChannel ch = controlChannel.get();
        if (ch == null) return;

        try {
            int sw = currentVideoWidth;
            int sh = currentVideoHeight;
            if (sw <= 0 || sh <= 0) {
                sw = 1080;
                sh = 1920;
            }

            float pressure = (action == ControlMessage.ACTION_MOVE_TOUCH) ? 0.5f : 1.0f;
            ControlMessage msg = ControlMessage.createInjectTouchEvent(
                    action,
                    ControlMessage.POINTER_ID_MOUSE,
                    x, y,
                    sw, sh,
                    pressure
            );
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: send touch failed - " + e.getMessage());
        }
    }

    public static void sendKeyDown(int keycode) {
        sendKey(ControlMessage.ACTION_DOWN, keycode);
    }

    public static void sendKeyUp(int keycode) {
        sendKey(ControlMessage.ACTION_UP, keycode);
    }

    private static void sendKey(int action, int keycode) {
        ControlChannel ch = controlChannel.get();
        if (ch == null) return;

        try {
            ControlMessage msg = ControlMessage.createInjectKeycode(
                    action,
                    keycode,
                    0,
                    AndroidKeyCode.META_NONE
            );
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: send key failed - " + e.getMessage());
        }
    }

    public static void sendText(String text) {
        ControlChannel ch = controlChannel.get();
        if (ch == null) return;

        try {
            ControlMessage msg = ControlMessage.createInjectText(text);
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: send text failed - " + e.getMessage());
        }
    }

    public static void sendBack() {
        ControlChannel ch = controlChannel.get();
        if (ch == null) return;

        try {
            ControlMessage msg = ControlMessage.createBackOrScreenOn(ControlMessage.ACTION_DOWN);
            ch.send(msg);
            Thread.sleep(50);
            msg = ControlMessage.createBackOrScreenOn(ControlMessage.ACTION_UP);
            ch.send(msg);
        } catch (Exception e) {
            Logger.error("control: send back failed - " + e.getMessage());
        }
    }

    public static void sendHome() {
        sendKeyDown(AndroidKeyCode.KEYCODE_HOME);
        try { Thread.sleep(50); } catch (InterruptedException ignore) {}
        sendKeyUp(AndroidKeyCode.KEYCODE_HOME);
    }

    public static void sendPower() {
        sendKeyDown(AndroidKeyCode.KEYCODE_POWER);
        try { Thread.sleep(50); } catch (InterruptedException ignore) {}
        sendKeyUp(AndroidKeyCode.KEYCODE_POWER);
    }

    public static void sendExpandNotification() {
        ControlChannel ch = controlChannel.get();
        if (ch == null) return;

        try {
            ControlMessage msg = ControlMessage.createEmpty(ControlMessage.TYPE_EXPAND_NOTIFICATION_PANEL);
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: expand notification failed - " + e.getMessage());
        }
    }

    public static void sendCollapsePanels() {
        ControlChannel ch = controlChannel.get();
        if (ch == null) return;

        try {
            ControlMessage msg = ControlMessage.createEmpty(ControlMessage.TYPE_COLLAPSE_PANELS);
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: collapse panels failed - " + e.getMessage());
        }
    }
}
