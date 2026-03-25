package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.GameMappingConfig;
import com.tyf.demo.service.ScrcpyService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.im.InputContext;
import java.util.Locale;


/**
 *   @desc : 主窗体入口
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class MainFrame extends JFrame {

    private static MainFrame mainFrame;
    private static Point savedWindowLocation;
    private static Dimension savedWindowSize;

    public MainFrame() {

        setTitle(ConstService.MAIN_TITLE);
        if (ConstService.MAX_WINDOW_WIDTH > 0 || ConstService.MAX_WINDOW_HEIGHT > 0) {
            setMaximumSize(new Dimension(
                ConstService.MAX_WINDOW_WIDTH > 0 ? ConstService.MAX_WINDOW_WIDTH : Integer.MAX_VALUE,
                ConstService.MAX_WINDOW_HEIGHT > 0 ? ConstService.MAX_WINDOW_HEIGHT : Integer.MAX_VALUE
            ));
        }
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(((ImageIcon) ConstService.MAIN_ICON).getImage());

        this.add(new MainPanel());
        this.pack();
        this.setLocationRelativeTo(null);
        mainFrame = this;
        registerGlobalMappingHotkey();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ScrcpyService.shutdown();
                ToolWindow.disposeToolWindow();
            }
        });
    }

    public static MainFrame getMainFrame() {
        return mainFrame;
    }

    /**
     *   @desc : 注册应用级快捷键 Ctrl+L，强制切换游戏映射模式
     *   @auth : tyf
     *   @date : 2026-03-20
     */
    private void registerGlobalMappingHotkey() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            // Ctrl+L：应用级快捷键，强制切换游戏映射模式（无论当前是否游戏模式都生效）
            if (e.getID() == KeyEvent.KEY_PRESSED
                    && e.getKeyCode() == KeyEvent.VK_L
                    && (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                GameMappingConfig.toggleMappingMode();
                boolean isGame = GameMappingConfig.isMappingMode();
                ToolWindow.updateMappingButtonIfExists(isGame);
                if (isGame && getContentPanel() != null) {
                    getContentPanel().requestFocusInWindow();
                }
                return true;
            }

            // 游戏模式下：键盘输入由 GLFW 捕获窗口处理。
            // Swing/AWT 这边直接吞掉所有键盘事件，避免输入法候选/组合键等干扰（WASD 触发拼音等）。
            if (GameMappingConfig.isMappingMode()) {
                return e.getID() == KeyEvent.KEY_PRESSED
                        || e.getID() == KeyEvent.KEY_RELEASED
                        || e.getID() == KeyEvent.KEY_TYPED;
            }

            if (e.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            return false;
        });
    }

    public ContentPanel getContentPanel() {
        return MainPanel.getContentPanel();
    }

    /**
     * @desc : 游戏模式下强制切换到英文输入法并禁用输入法通道，避免 WASD/Shift 等触发输入法候选
     * @auth : tyf
     * @date : 2026-03-25
     */
    public static void applyImePolicyForMappingMode(boolean mappingMode) {
        if (mainFrame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                // 1) 禁用 AWT/Swing 的输入法通道（对大多数输入法候选弹窗有明显抑制作用）
                mainFrame.enableInputMethods(!mappingMode);
                if (mainFrame.getContentPanel() != null) {
                    mainFrame.getContentPanel().enableInputMethods(!mappingMode);
                }

                // 2) 尝试把本进程输入法切换到英文（部分输入法 Shift 切换会影响后续组合态）
                if (mappingMode) {
                    InputContext ic = InputContext.getInstance();
                    if (ic != null) {
                        ic.selectInputMethod(Locale.ENGLISH);
                    }
                    InputContext ic2 = mainFrame.getInputContext();
                    if (ic2 != null) {
                        ic2.selectInputMethod(Locale.ENGLISH);
                    }
                    if (mainFrame.getContentPanel() != null) {
                        InputContext ic3 = mainFrame.getContentPanel().getInputContext();
                        if (ic3 != null) {
                            ic3.selectInputMethod(Locale.ENGLISH);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     *   @desc : 更新窗口标题
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param deviceName : 设备名称（传入null则恢复默认标题）
     */
    public static void updateTitle(String deviceName) {
        if (mainFrame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (deviceName == null || deviceName.trim().isEmpty()) {
                mainFrame.setTitle(ConstService.MAIN_TITLE);
            } else {
                mainFrame.setTitle(ConstService.MAIN_TITLE + " - " + deviceName);
            }
        });
    }

    public static void saveWindowPositionBeforeModeSwitch() {
        if (mainFrame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            savedWindowLocation = mainFrame.getLocation();
            savedWindowSize = mainFrame.getSize();
        });
    }

    public static void restoreWindowPositionAfterModeSwitch() {
        if (mainFrame == null || savedWindowLocation == null || savedWindowSize == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            mainFrame.setLocation(savedWindowLocation);
            mainFrame.setSize(savedWindowSize);
        });
    }

    /**
     *   @desc : 横竖屏切换时调整窗口大小
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
     *   @param w 视频宽度
     *   @param h 视频高度
     */
    public static void resizeForContent(int w, int h) {
        // 保险：当关闭自动适配时，任何地方误调用也不允许改窗口尺寸
        if (!ConstService.AUTO_RESIZE_WINDOW) {
            return;
        }
        if (mainFrame == null || w <= 0 || h <= 0) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Insets insets = mainFrame.getInsets();
            Dimension currentSize = mainFrame.getSize();
            Dimension currentContent = mainFrame.getContentPane().getSize();

            int targetContentW = w;
            int targetContentH = h;

            if (currentContent.width == targetContentW && currentContent.height == targetContentH) {
                return;
            }

            int newWindowW = targetContentW + insets.left + insets.right;
            int newWindowH = targetContentH + insets.top + insets.bottom;

            Dimension maxSize = mainFrame.getMaximumSize();
            if (maxSize.width > 0) newWindowW = Math.min(newWindowW, maxSize.width);
            if (maxSize.height > 0) newWindowH = Math.min(newWindowH, maxSize.height);

            if (currentSize.width == newWindowW && currentSize.height == newWindowH) return;

            mainFrame.setSize(newWindowW, newWindowH);
            // 只调整大小，不自动移动窗口位置：
            // - 避免在首次连接/切换模式时出现“窗口跳到屏幕中间”的体验
            // - 也避免多次 resize 时位置逐渐漂移
        });
    }
}
