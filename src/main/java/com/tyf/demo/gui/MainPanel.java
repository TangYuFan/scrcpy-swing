package com.tyf.demo.gui;

import com.tyf.demo.service.ConnectService;
import com.tyf.demo.service.ScrcpyService;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;


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

    public static MainPanel getMainPanel(){
        return panel;
    }

    public static ContentPanel getContentPanel(){
        return contentPanel;
    }

}
