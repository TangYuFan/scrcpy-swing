package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ScrcpyService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


/**
 *   @desc : 主窗体入口
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class MainFrame extends JFrame {

    private static MainFrame mainFrame;

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

    public ContentPanel getContentPanel() {
        return MainPanel.getContentPanel();
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

    /**
     *   @desc : 横竖屏切换时调整窗口大小
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
     *   @param w 视频宽度
     *   @param h 视频高度
     */
    public static void resizeForContent(int w, int h) {
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

            Point center = mainFrame.getLocation();
            mainFrame.setSize(newWindowW, newWindowH);
            mainFrame.setLocation(center.x + (currentSize.width - newWindowW) / 2, 
                                   center.y + (currentSize.height - newWindowH) / 2);
        });
    }
}
