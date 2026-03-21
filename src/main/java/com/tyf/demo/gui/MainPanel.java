package com.tyf.demo.gui;

import com.tyf.demo.service.ConnectService;
import com.tyf.demo.service.ScrcpyService;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;


/**
 *   @desc : 主要应用窗口
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
 */
public class MainPanel extends JPanel {

    private static MainPanel panel;
    private static ContentPanel contentPanel;

    public MainPanel() {

        // 垂直布局：上、中、下 依次排列
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setLayout(new BorderLayout());

        // 1. 顶部工具栏
        TopPanel topPanel = new TopPanel();
        add(topPanel, BorderLayout.NORTH);

        // 2. 中间内容区（手机画面）
        contentPanel = new ContentPanel();
        add(contentPanel, BorderLayout.CENTER);

        // 3. 底部工具栏（链接）
        BottomPanel bottomPanel = new BottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        panel = this;

        // 注册设备断开监听器
        registerDisconnectListener();

    }

    /**
     *   @desc : 注册设备断开监听器
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    private void registerDisconnectListener() {
        ScrcpyService.setOnDisconnectListener(reason -> {
            Logger.info("Device auto disconnected: " + reason);
            // 在 UI 线程中执行清理操作
            SwingUtilities.invokeLater(() -> {
                // 关闭 loading 对话框（如果还在显示）
                ContentPanel.closeLoadingDialog();
                // 关闭 scrcpy 服务，清除所有连接资源
                ScrcpyService.shutdown();
                // 重置连接状态，允许重新连接
                ConnectService.resetConnecting();
                // 清除中间画面
                if (contentPanel != null) {
                    contentPanel.reset();
                    contentPanel.repaint();
                }
            });
        });
    }

    /**
     *   @desc : 获取主面板实例
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static MainPanel getMainPanel(){
        return panel;
    }

    /**
     *   @desc : 获取内容面板实例
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static ContentPanel getContentPanel(){
        return contentPanel;
    }

}
