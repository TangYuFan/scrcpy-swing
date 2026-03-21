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
        // 设置最大窗口尺寸（0 = 不限制）
        if (ConstService.MAX_WINDOW_WIDTH > 0 || ConstService.MAX_WINDOW_HEIGHT > 0) {
            setMaximumSize(new Dimension(
                ConstService.MAX_WINDOW_WIDTH > 0 ? ConstService.MAX_WINDOW_WIDTH : Integer.MAX_VALUE,
                ConstService.MAX_WINDOW_HEIGHT > 0 ? ConstService.MAX_WINDOW_HEIGHT : Integer.MAX_VALUE
            ));
        }
        setLocationRelativeTo(null);     // 居中
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(((ImageIcon) ConstService.MAIN_ICON).getImage());  // 图标

        // 窗体入口
        this.add(new MainPanel());
        this.pack();
        this.setLocationRelativeTo(null);
        mainFrame = this;

        // 退出前结束 adb shell / 端口转发，避免 adb.exe 仍被占用导致下次启动无法覆盖
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ScrcpyService.shutdown();
            }
        });
    }

    /**
     *   @desc : 获取主窗体实例
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static MainFrame getMainFrame() {
        return mainFrame;
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

            // 根据横竖屏确定目标内容尺寸（固定值，不跟随手机分辨率）
            int targetContentW, targetContentH;
            if (w > h) {
                // 横屏
                targetContentW = ConstService.MAIN_HEIGHT;
                targetContentH = ConstService.MAIN_WIDTH;
            } else {
                // 竖屏
                targetContentW = ConstService.MAIN_WIDTH;
                targetContentH = ConstService.MAIN_HEIGHT;
            }

            // 如果内容区尺寸没变化则跳过
            if (currentContent.width == targetContentW && currentContent.height == targetContentH) {
                return;
            }

            int newWindowW = targetContentW + insets.left + insets.right;
            int newWindowH = targetContentH + insets.top + insets.bottom;

            // 限制最大尺寸
            Dimension maxSize = mainFrame.getMaximumSize();
            if (maxSize.width > 0) newWindowW = Math.min(newWindowW, maxSize.width);
            if (maxSize.height > 0) newWindowH = Math.min(newWindowH, maxSize.height);

            if (currentSize.width == newWindowW && currentSize.height == newWindowH) return;

            // 保持窗口居中
            Point center = mainFrame.getLocation();
            mainFrame.setSize(newWindowW, newWindowH);
            mainFrame.setLocation(center.x + (currentSize.width - newWindowW) / 2, 
                                   center.y + (currentSize.height - newWindowH) / 2);
        });
    }
}
