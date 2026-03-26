package com.tyf.demo.service;

import com.tyf.demo.gui.MainPanel;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.pmw.tinylog.Logger;

import java.awt.Rectangle;

import static org.lwjgl.glfw.GLFW.*;

/**
 * @desc : GLFW 捕获窗口：鼠标锁定/隐藏/高频 delta
 * @auth : tyf
 * @date : 2026-03-25
 */
public class GlfwInputCaptureService {

    private static volatile boolean running;
    private static volatile long window;
    private static volatile Thread loopThread;

    private static volatile double lastX;
    private static volatile double lastY;
    private static volatile boolean hasLast;

    private GlfwInputCaptureService() {}

    public static synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        loopThread = new Thread(GlfwInputCaptureService::runLoop, "glfw-input-capture");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (window != 0L) {
                glfwSetWindowShouldClose(window, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void runLoop() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            Logger.error("glfw: init failed");
            running = false;
            return;
        }
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
            glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_TRUE);
            glfwWindowHint(GLFW_FLOATING, GLFW_TRUE);
            glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);
            glfwWindowHint(GLFW_SAMPLES, 0);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

            // 初始创建一个很小的窗口，随后根据 Swing 视频区域动态移动/缩放
            window = glfwCreateWindow(200, 200, "capture", 0L, 0L);
            if (window == 0L) {
                Logger.error("glfw: create window failed");
                running = false;
                return;
            }

            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            if (glfwRawMouseMotionSupported()) {
                glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
            }

            glfwSetCursorPosCallback(window, (GLFWCursorPosCallbackI) (w, x, y) -> {
                if (!GameMappingConfig.isMappingMode()) {
                    hasLast = false;
                    return;
                }
                if (!hasLast) {
                    hasLast = true;
                    lastX = x;
                    lastY = y;
                    return;
                }
                double dx = x - lastX;
                double dy = y - lastY;
                lastX = x;
                lastY = y;
                int idx = (int) Math.round(dx);
                int idy = (int) Math.round(dy);
                if (idx != 0 || idy != 0) {
                    GameMappingService.handleMouseMoved(idx, idy);
                }
            });

            glfwSetKeyCallback(window, (GLFWKeyCallbackI) (w, key, scancode, action, mods) -> {
                if (!GameMappingConfig.isMappingMode()) {
                    return;
                }
                // ESC：直接退出映射模式（并在 setMappingMode(false) 里 stop 捕获窗口）
                if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                    GameMappingConfig.setMappingMode(false);
                    // 双保险：立刻请求关闭捕获窗口
                    GlfwInputCaptureService.stop();
                    return;
                }
                // GLFW key 与 AWT KeyEvent 不完全一致，这里先用常用键位映射：WASD/QE/Shift/Ctrl/Space/Esc
                int awtKey = GlfwKeyMapper.toAwtKeyCode(key);
                if (awtKey <= 0) {
                    return;
                }
                if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                    GameMappingService.handleKeyPressed(awtKey);
                } else if (action == GLFW_RELEASE) {
                    GameMappingService.handleKeyReleased(awtKey);
                }
            });

            glfwSetMouseButtonCallback(window, (GLFWMouseButtonCallbackI) (w, button, action, mods) -> {
                if (!GameMappingConfig.isMappingMode()) {
                    return;
                }
                int awtButton = 0;
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    awtButton = java.awt.event.MouseEvent.BUTTON1;
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    awtButton = java.awt.event.MouseEvent.BUTTON3;
                } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                    awtButton = java.awt.event.MouseEvent.BUTTON2;
                }
                if (awtButton == 0) {
                    return;
                }
                if (action == GLFW_PRESS) {
                    GameMappingService.handleMousePressed(awtButton);
                } else if (action == GLFW_RELEASE) {
                    GameMappingService.handleMouseReleased(awtButton);
                }
            });

            glfwSetScrollCallback(window, (GLFWScrollCallbackI) (w, xoffset, yoffset) -> {
                // 目前映射模式下滚轮不做处理；若后续需要（切武器/缩放等）可在此映射为触控或按键
            });

            while (running && !glfwWindowShouldClose(window)) {
                // 同步捕获窗口位置/大小到 Swing 视频区域
                Rectangle r = null;
                try {
                    if (MainPanel.getContentPanel() != null) {
                        r = MainPanel.getContentPanel().getVideoSurfaceBoundsOnScreen();
                    }
                } catch (Throwable ignored) {
                }
                if (r != null && r.width > 0 && r.height > 0) {
                    glfwSetWindowPos(window, r.x, r.y);
                    glfwSetWindowSize(window, r.width, r.height);
                }

                glfwPollEvents();
                try {
                    Thread.sleep(8);
                } catch (InterruptedException ignored) {
                }
            }
        } finally {
            long w = window;
            window = 0L;
            hasLast = false;
            try {
                if (w != 0L) {
                    glfwDestroyWindow(w);
                }
            } catch (Throwable ignored) {
            }
            try {
                glfwTerminate();
            } catch (Throwable ignored) {
            }
            running = false;
        }
    }
}

