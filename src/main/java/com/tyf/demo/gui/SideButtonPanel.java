package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ControlService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 *   @desc : 右侧侧边按钮面板（连接时显示，提供快捷操作按钮）
 *   @auth : tyf
 *   @date : 2026-03-21
 */
public class SideButtonPanel extends JPanel {

    /** 面板宽度 */
    private static final int PANEL_WIDTH = 36;

    public SideButtonPanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setLayout(new GridBagLayout());
        setBackground(ConstService.THEME_SURFACE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        add(createToolButton("\u2302", "Home", e -> ControlService.sendHome()), gbc);
        gbc.gridy++;
        add(createToolButton("\u21A9", "Back", e -> ControlService.sendBack()), gbc);
        gbc.gridy++;
        add(createToolButton("\u21C4", "Switch", e -> {
            ControlService.sendKeyDown(187);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(187);
        }), gbc);
        gbc.gridy++;
        add(createToolButton("\u23FB", "Power", e -> ControlService.sendPower()), gbc);
        gbc.gridy++;
        add(createToolButton("\u2191", "Vol+", e -> {
            ControlService.sendKeyDown(24);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(24);
        }), gbc);
        gbc.gridy++;
        add(createToolButton("\u2193", "Vol-", e -> {
            ControlService.sendKeyDown(25);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(25);
        }), gbc);
        gbc.gridy++;
        add(createToolButton("\u2709", "Notify", e -> ControlService.sendExpandNotification()), gbc);
    }

    private JButton createToolButton(String icon, String tooltip, ActionListener action) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
        btn.setFocusable(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(PANEL_WIDTH - 6, 32));
        btn.setMaximumSize(new Dimension(PANEL_WIDTH - 6, 32));
        btn.setMinimumSize(new Dimension(PANEL_WIDTH - 6, 32));
        
        btn.setBackground(ConstService.THEME_SURFACE);
        btn.setForeground(ConstService.THEME_TEXT_PRIMARY);
        btn.setBorder(BorderFactory.createLineBorder(ConstService.THEME_BORDER, 1));
        
        btn.addActionListener(action);
        
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(ConstService.THEME_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(ConstService.THEME_SURFACE);
            }
        });
        
        return btn;
    }
}
