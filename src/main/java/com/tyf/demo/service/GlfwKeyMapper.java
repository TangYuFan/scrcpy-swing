package com.tyf.demo.service;

import java.awt.event.KeyEvent;

import static org.lwjgl.glfw.GLFW.*;

/**
 * @desc : GLFW 键码 -> AWT KeyEvent 键码（仅映射常用游戏键）
 * @auth : tyf
 * @date : 2026-03-25
 */
public class GlfwKeyMapper {

    private GlfwKeyMapper() {}

    public static int toAwtKeyCode(int glfwKey) {
        switch (glfwKey) {
            // 字母键 A-Z
            case GLFW_KEY_A:
                return KeyEvent.VK_A;
            case GLFW_KEY_B:
                return KeyEvent.VK_B;
            case GLFW_KEY_C:
                return KeyEvent.VK_C;
            case GLFW_KEY_D:
                return KeyEvent.VK_D;
            case GLFW_KEY_E:
                return KeyEvent.VK_E;
            case GLFW_KEY_F:
                return KeyEvent.VK_F;
            case GLFW_KEY_G:
                return KeyEvent.VK_G;
            case GLFW_KEY_H:
                return KeyEvent.VK_H;
            case GLFW_KEY_I:
                return KeyEvent.VK_I;
            case GLFW_KEY_J:
                return KeyEvent.VK_J;
            case GLFW_KEY_K:
                return KeyEvent.VK_K;
            case GLFW_KEY_L:
                return KeyEvent.VK_L;
            case GLFW_KEY_M:
                return KeyEvent.VK_M;
            case GLFW_KEY_N:
                return KeyEvent.VK_N;
            case GLFW_KEY_O:
                return KeyEvent.VK_O;
            case GLFW_KEY_P:
                return KeyEvent.VK_P;
            case GLFW_KEY_Q:
                return KeyEvent.VK_Q;
            case GLFW_KEY_R:
                return KeyEvent.VK_R;
            case GLFW_KEY_S:
                return KeyEvent.VK_S;
            case GLFW_KEY_T:
                return KeyEvent.VK_T;
            case GLFW_KEY_U:
                return KeyEvent.VK_U;
            case GLFW_KEY_V:
                return KeyEvent.VK_V;
            case GLFW_KEY_W:
                return KeyEvent.VK_W;
            case GLFW_KEY_X:
                return KeyEvent.VK_X;
            case GLFW_KEY_Y:
                return KeyEvent.VK_Y;
            case GLFW_KEY_Z:
                return KeyEvent.VK_Z;
            case GLFW_KEY_SPACE:
                return KeyEvent.VK_SPACE;
            case GLFW_KEY_LEFT_SHIFT:
            case GLFW_KEY_RIGHT_SHIFT:
                return KeyEvent.VK_SHIFT;
            case GLFW_KEY_LEFT_CONTROL:
            case GLFW_KEY_RIGHT_CONTROL:
                return KeyEvent.VK_CONTROL;
            case GLFW_KEY_TAB:
                return KeyEvent.VK_TAB;
            case GLFW_KEY_ESCAPE:
                return KeyEvent.VK_ESCAPE;
            default:
                return 0;
        }
    }
}

