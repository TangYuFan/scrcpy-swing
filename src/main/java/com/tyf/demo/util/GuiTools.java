package com.tyf.demo.util;

import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.service.ConstService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GuiTools {

    // 创建一个超链接
    public static JLabel createLinkLabel(String text, Font font, Color color) {
        JLabel label = new JLabel("<html><a href='' style='color:rgb(" +
                color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ");'>" +
                text + "</a></html>");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setFont(font);
        return label;
    }


    public static JLabel createLinkLabelNoUnderline(String text, Font font, Color color) {
        JLabel label = new JLabel("<html><a href='' style='text-decoration:none; color:rgb(" +
                color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ");'>" +
                text + "</a></html>");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setFont(font);
        return label;
    }


    // 弹出加载框
    public static JDialog showLoading(String text, Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);

        JDialog dialog = new JDialog(owner);
        dialog.setUndecorated(true);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new JPanel() {
            private float angle = 0f;

            {
                new Timer(16, e -> {
                    angle += 0.15f;
                    repaint();
                }).start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 背景
                g2.setColor(new Color(40, 40, 40, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

                // 旋转圈
                int cx = getWidth() / 2;
                int cy = getHeight() / 2 - 8;

                g2.setStroke(new BasicStroke(3f));
                g2.setColor(Color.WHITE);
                g2.drawArc(cx - 10, cy - 10, 20, 20,
                        (int) (angle * 50), 270);

                // 文字
                g2.setFont(ConstService.FONT_NORMAL);
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(text);

                g2.drawString(text,
                        (getWidth() - textWidth) / 2,
                        cy + 30);
            }
        };

        panel.setPreferredSize(new Dimension(160, 80));
        panel.setOpaque(false);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        // ✅ 跟随父窗口移动
        ComponentListener listener = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                dialog.setLocationRelativeTo(parent);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                dialog.setLocationRelativeTo(parent);
            }
        };

        parent.addComponentListener(listener);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                parent.removeComponentListener(listener);
            }
        });

        dialog.setVisible(true);
        return dialog;
    }

}
