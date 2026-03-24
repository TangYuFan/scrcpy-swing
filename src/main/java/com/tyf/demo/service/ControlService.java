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

    private static final int CONTROL_PORT = ConstService.SCRCPY_PORT;

    private static final AtomicReference<Socket> controlSocket = new AtomicReference<>();
    private static final AtomicReference<ControlChannel> controlChannel = new AtomicReference<>();
    private static final AtomicReference<Thread> controlThread = new AtomicReference<>();

    private static volatile int currentVideoWidth;
    private static volatile int currentVideoHeight;
    private static volatile boolean videoRotation = false;

    // 用于拖拽时的状态跟踪
    private static volatile boolean isDragging = false;

    private ControlService() {}

    public static boolean isConnected() {
        return controlChannel.get() != null;
    }

    /**
     *   @desc : 启动控制服务（创建新连接）
     *   @auth : tyf
     *   @date : 2026-03-21
     */
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

    /**
     *   @desc : 使用已有的 socket 启动控制服务
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param sock : 已建立的控制 socket
     *   @param deviceId : 设备ID
     */
    public static void startControlWithSocket(Socket sock, String deviceId) {
        try {
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(0); // 无超时
            controlSocket.set(sock);

            OutputStream out = sock.getOutputStream();
            ControlChannel channel = new ControlChannel(out);
            controlChannel.set(channel);

            Logger.info("control: connected with existing socket, output stream ready");
        } catch (IOException e) {
            Logger.error("control: init failed - " + e.getMessage());
        }
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
        isDragging = false;
        Logger.info("control: shutdown");
    }

    public static void updateVideoSize(int width, int height) {
        currentVideoWidth = width;
        currentVideoHeight = height;
        // Logger.debug("control: video size " + width + "x" + height);
    }

    public static void setVideoRotation(boolean rotation) {
        videoRotation = rotation;
    }

    /**
     *   @desc : 触摸按下（鼠标左键按下）
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static void sendTouchDown(int x, int y, int actionButton) {
        sendTouchDown(ControlMessage.POINTER_ID_MOUSE, x, y, actionButton);
        isDragging = true;
    }

    public static void sendTouchDown(long pointerId, int x, int y, int actionButton) {
        sendTouch(pointerId, ControlMessage.ACTION_DOWN_TOUCH, x, y, actionButton, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
    }

    /**
     *   @desc : 触摸释放（鼠标左键释放）
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static void sendTouchUp(int x, int y) {
        sendTouchUp(ControlMessage.POINTER_ID_MOUSE, x, y);
        isDragging = false;
    }

    public static void sendTouchUp(long pointerId, int x, int y) {
        sendTouch(pointerId, ControlMessage.ACTION_UP_TOUCH, x, y, 0, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
    }

    /**
     *   @desc : 触摸移动（鼠标拖拽）
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static void sendTouchMove(int x, int y) {
        sendTouchMove(ControlMessage.POINTER_ID_MOUSE, x, y);
    }

    public static void sendTouchMove(long pointerId, int x, int y) {
        sendTouch(pointerId, ControlMessage.ACTION_MOVE_TOUCH, x, y, 0, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
    }

    /**
     *   @desc : 右键按下
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static void sendTouchDownRight(int x, int y) {
        sendTouch(ControlMessage.POINTER_ID_MOUSE, ControlMessage.ACTION_DOWN_TOUCH, x, y, ControlMessage.AMOTION_EVENT_BUTTON_SECONDARY, ControlMessage.AMOTION_EVENT_BUTTON_SECONDARY);
    }

    /**
     *   @desc : 右键释放
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static void sendTouchUpRight(int x, int y) {
        sendTouch(ControlMessage.POINTER_ID_MOUSE, ControlMessage.ACTION_UP_TOUCH, x, y, 0, ControlMessage.AMOTION_EVENT_BUTTON_SECONDARY);
    }

    private static void sendTouch(long pointerId, int action, int x, int y, int actionButton, int buttons) {
        ControlChannel ch = controlChannel.get();
        if (ch == null) {
            // 高频路径不打日志，避免实时输入被 I/O 拖慢
            return;
        }

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
                    pointerId,
                    x, y,
                    sw, sh,
                    pressure,
                    actionButton,
                    buttons
            );
//            Logger.debug("control: sendTouch action=" + action + " x=" + x + " y=" + y + " sw=" + sw + " sh=" + sh);
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: send touch failed - " + e.getMessage());
        }
    }

    /**
     *   @desc : 发送滚动事件
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static void sendScroll(int x, int y, float hScroll, float vScroll) {
        ControlChannel ch = controlChannel.get();
        if (ch == null) {
            return;
        }

        try {
            int sw = currentVideoWidth;
            int sh = currentVideoHeight;
            if (sw <= 0 || sh <= 0) {
                sw = 1080;
                sh = 1920;
            }

            ControlMessage msg = ControlMessage.createInjectScrollEvent(
                    x, y,
                    sw, sh,
                    hScroll, vScroll,
                    ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY
            );
            // Logger.debug("control: sendScroll x=" + x + " y=" + y + " hScroll=" + hScroll + " vScroll=" + vScroll);
            ch.send(msg);
        } catch (IOException e) {
            Logger.error("control: send scroll failed - " + e.getMessage());
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

    /**
     *   @desc : 返回键（屏幕亮时按返回，屏幕暗时亮屏）
     *   @auth : tyf
     *   @date : 2026-03-21
     */
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

    /**
     *   @desc : 检查是否正在拖拽
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public static boolean isDragging() {
        return isDragging;
    }
}
