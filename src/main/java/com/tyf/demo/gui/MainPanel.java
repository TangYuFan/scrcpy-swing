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

        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setLayout(new BorderLayout());

        TopPanel topPanel = new TopPanel();
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        contentPanel = new ContentPanel();
        centerPanel.add(contentPanel, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        BottomPanel bottomPanel = new BottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        panel = this;

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
            SwingUtilities.invokeLater(() -> {
                ToolWindow.hideToolWindow();
                ContentPanel.closeLoadingDialog();
                ScrcpyService.shutdown();
                ConnectService.resetConnecting();
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
