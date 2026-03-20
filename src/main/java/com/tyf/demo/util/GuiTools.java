package com.tyf.demo.util;

import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.service.ConstService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GuiTools {

    /**
     *   @desc : 创建超链接
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static JLabel createLinkLabel(String text, Font font, Color color) {
        JLabel label = new JLabel("<html><a href='' style='color:rgb(" +
                color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ");'>" +
                text + "</a></html>");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setFont(font);
        return label;
    }


    /**
     *   @desc : 创建无下划线超链接
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static JLabel createLinkLabelNoUnderline(String text, Font font, Color color) {
        JLabel label = new JLabel("<html><a href='' style='text-decoration:none; color:rgb(" +
                color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ");'>" +
                text + "</a></html>");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setFont(font);
        return label;
    }


    /**
     *   @desc : 弹出加载框
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
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

                int w = getWidth();
                int h = getHeight();

                // 背景
                g2.setColor(new Color(60, 60, 65));
                g2.fillRoundRect(0, 0, w, h, 16, 16);

                // 加载圈
                int cx = w / 2;
                int cy = h / 2 - 8;

                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(100, 100, 105));
                g2.drawArc(cx - 10, cy - 10, 20, 20, 0, 360);

                // 渐变色旋转弧
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int arcLen = 180;
                for (int i = 0; i < arcLen; i += 8) {
                    float t = (i + angle * 100) % arcLen / (float) arcLen;
                    g2.setColor(new Color(100, 160, 255, (int) (255 * (1 - t * 0.4f))));
                    g2.drawArc(cx - 10, cy - 10, 20, 20,
                            (int) (-90 - angle * 50 + i), 8);
                }

                // 文字
                g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
                g2.setColor(new Color(220, 220, 225));
                FontMetrics fm = g2.getFontMetrics();
                String displayText = "Connecting...";
                int textWidth = fm.stringWidth(displayText);
                g2.drawString(displayText, (w - textWidth) / 2, cy + 30);
            }
        };

        panel.setPreferredSize(new Dimension(160, 80));
        panel.setOpaque(false);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        // 跟随父窗口移动
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
