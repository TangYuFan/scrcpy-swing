package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ControlService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 *   @desc : 独立浮动工具窗口（跟随主窗口，显示手机控制按钮）
 *   @auth : tyf
 *   @date : 2026-03-23
 */
public class ToolWindow extends JDialog {

    private static ToolWindow instance;
    private static JFrame ownerFrame;
    private static ComponentListener positionSyncListener;

    private ToolWindow(JFrame owner) {
        super(owner, "", Dialog.ModalityType.MODELESS);
        
        setUndecorated(true);
        setResizable(false);
        setBackground(new Color(0, 0, 0, 0));

        int arc = 12;
        
        JPanel mainPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ConstService.THEME_SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        mainPanel.setOpaque(true);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        mainPanel.add(createToolButton("\u2302", "Home", e -> ControlService.sendHome()), gbc);
        gbc.gridy++;
        mainPanel.add(createToolButton("\u21A9", "Back", e -> ControlService.sendBack()), gbc);
        gbc.gridy++;
        mainPanel.add(createToolButton("\u21C4", "Switch", e -> {
            ControlService.sendKeyDown(187);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(187);
        }), gbc);
        gbc.gridy++;
        mainPanel.add(createToolButton("\u23FB", "Power", e -> ControlService.sendPower()), gbc);
        gbc.gridy++;
        mainPanel.add(createToolButton("\u2191", "Vol+", e -> {
            ControlService.sendKeyDown(24);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(24);
        }), gbc);
        gbc.gridy++;
        mainPanel.add(createToolButton("\u2193", "Vol-", e -> {
            ControlService.sendKeyDown(25);
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            ControlService.sendKeyUp(25);
        }), gbc);
        gbc.gridy++;
        mainPanel.add(createToolButton("\u2709", "Notify", e -> ControlService.sendExpandNotification()), gbc);

        setContentPane(mainPanel);
        
        getRootPane().setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        
        pack();
        
        setupPositionSync(owner);
    }

    private void setupPositionSync(JFrame owner) {
        ownerFrame = owner;
        
        positionSyncListener = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                syncPosition();
            }
            @Override
            public void componentResized(ComponentEvent e) {
                syncPosition();
            }
        };
        
        owner.addComponentListener(positionSyncListener);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (ownerFrame != null) {
                    ownerFrame.removeComponentListener(positionSyncListener);
                }
            }
        });

        syncPosition();
    }

    private void syncPosition() {
        if (ownerFrame == null || !ownerFrame.isVisible()) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            Point ownerLoc = ownerFrame.getLocationOnScreen();
            Dimension ownerSize = ownerFrame.getSize();
            Dimension mySize = getSize();
            
            int x = ownerLoc.x + ownerSize.width + 2;
            int y = ownerLoc.y + (ownerSize.height - mySize.height) / 2;
            
            setLocation(x, y);
        });
    }

    private JButton createToolButton(String icon, String tooltip, ActionListener action) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 18));
        btn.setFocusable(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setMaximumSize(new Dimension(30, 30));
        btn.setMinimumSize(new Dimension(30, 30));
        
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

    /**
     *   @desc : 显示浮动窗口（连接成功时调用）
     *   @auth : tyf
     *   @date : 2026-03-23
     */
    public static void showToolWindow(JFrame owner) {
        if (instance != null && instance.isVisible()) {
            return;
        }
        
        if (instance == null || !instance.getOwner().equals(owner)) {
            if (instance != null) {
                instance.dispose();
            }
            instance = new ToolWindow(owner);
        }
        
        instance.setVisible(true);
    }

    /**
     *   @desc : 隐藏浮动窗口（断开连接时调用）
     *   @auth : tyf
     *   @date : 2026-03-23
     */
    public static void hideToolWindow() {
        if (instance != null) {
            instance.setVisible(false);
        }
    }

    /**
     *   @desc : 彻底关闭并释放资源
     *   @auth : tyf
     *   @date : 2026-03-23
     */
    public static void disposeToolWindow() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }
}
