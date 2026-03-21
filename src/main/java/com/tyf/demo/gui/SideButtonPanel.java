package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ControlService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 *   @desc : 右侧侧边按钮面板（连接时显示，提供快捷操作按钮）
 *   @auth : tyf
 *   @date : 2026-03-21
 */
public class SideButtonPanel extends JPanel {

    /** 面板宽度 */
    private static final int PANEL_WIDTH = 50;

    public SideButtonPanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setLayout(new GridBagLayout());
        setBackground(new Color(240, 240, 240));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Home 按钮
        add(createToolButton("\u2302", "Home", e -> ControlService.sendHome()), gbc);
        gbc.gridy++;

        // Back 按钮
        add(createToolButton("\u21A9", "Back", e -> ControlService.sendBack()), gbc);
        gbc.gridy++;

        // Switch 按钮
        add(createToolButton("\u21C4", "Switch", e -> {
            ControlService.sendKeyDown(187);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(187);
        }), gbc);
        gbc.gridy++;

        // Power 按钮
        add(createToolButton("\u23FB", "Power", e -> ControlService.sendPower()), gbc);
        gbc.gridy++;

        // Vol+ 按钮
        add(createToolButton("\u2191", "Vol+", e -> {
            ControlService.sendKeyDown(24);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(24);
        }), gbc);
        gbc.gridy++;

        // Vol- 按钮
        add(createToolButton("\u2193", "Vol-", e -> {
            ControlService.sendKeyDown(25);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(25);
        }), gbc);
        gbc.gridy++;

        // Notify 按钮
        add(createToolButton("\u2709", "Notify", e -> ControlService.sendExpandNotification()), gbc);
    }

    /**
     *   @desc : 创建工具按钮
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    private JButton createToolButton(String icon, String tooltip, ActionListener action) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 18));
        btn.setFocusable(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(PANEL_WIDTH - 8, 36));
        btn.setMaximumSize(new Dimension(PANEL_WIDTH - 8, 36));
        btn.setMinimumSize(new Dimension(PANEL_WIDTH - 8, 36));
        btn.addActionListener(action);
        return btn;
    }
}
