package com.tyf.demo.gui;


import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.InitService;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 *   @desc : 初始化进度窗口
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class InitPanel extends JDialog {

    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    public InitPanel(JFrame parent) {

        super(parent, false);
        setUndecorated(true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setBackground(new Color(0, 0, 0, 0));
        setVisible(true);

        int arc = 16;

        JPanel content = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int shadow = 6;
                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(shadow, shadow, getWidth() - shadow * 2, getHeight() - shadow * 2, arc, arc);
                g2.setColor(ConstService.THEME_SURFACE);
                g2.fillRoundRect(0, 0, getWidth() - shadow, getHeight() - shadow, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(16, 28, 20, 28));

        statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);
        statusLabel.setFont(ConstService.FONT_NORMAL);
        statusLabel.setForeground(ConstService.THEME_TEXT_PRIMARY);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBackground(ConstService.THEME_BORDER);
        progressBar.setForeground(ConstService.THEME_PRIMARY);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(200, 6));

        content.add(statusLabel, BorderLayout.NORTH);
        content.add(progressBar, BorderLayout.CENTER);

        JPanel outerPanel = new JPanel(new GridBagLayout());
        outerPanel.setOpaque(false);
        outerPanel.add(content);

        setContentPane(outerPanel);
        pack();
        setLocationRelativeTo(parent);

        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
    }

    /**
     *   @desc : 更新进度（不带百分比文字）
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void updateProgress(int value, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            if (message != null && !message.isEmpty()) {
                statusLabel.setText(message);
            }
        });
    }

    /**
     *   @desc : 完成后自动关闭
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void finish() {
        SwingUtilities.invokeLater(this::dispose);
    }

    /**
     *   @desc : 模拟任务
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void start() {

        // Task execution progress
        Consumer<Integer> progress = i ->{
            updateProgress(i,"Initializing...");
        };

        // 任务执行完成
        Consumer<Void> finish = Void ->{
            finish();
        };

        // 执行任务
        new Thread(()->{
            InitService.init(progress,finish);
        }).start();
    }

}
