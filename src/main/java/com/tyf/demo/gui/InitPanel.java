package com.tyf.demo.gui;


import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.InitService;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * @desc 初始化进度窗口
 * @auth tyf
 * @date 2025-10-22
 */
public class InitPanel extends JDialog {

    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    public InitPanel(JFrame parent) {

        super(parent, false);
        setUndecorated(true); // 去掉系统边框、标题栏
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setBackground(new Color(0, 0, 0, 0)); // 背景完全透明（关键）
        setVisible(true); // 显示进度窗口

        int arc = 20; // 圆角半径

        // 圆角内容面板
        JPanel content = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 绘制圆角白色背景 + 阴影
                int shadow = 4;
                g2.setColor(new Color(0, 0, 0, 40)); // 阴影层
                g2.fillRoundRect(shadow, shadow, getWidth() - shadow * 2, getHeight() - shadow * 2, arc, arc);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - shadow, getHeight() - shadow, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(10, 25, 20, 25));

        // 状态文字
        statusLabel = new JLabel("初始化中...", SwingConstants.CENTER);
        statusLabel.setFont(ConstService.FONT_SMALL);

        // 进度条（显示百分比）
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        // 添加组件
        content.add(statusLabel, BorderLayout.NORTH);
        content.add(progressBar, BorderLayout.CENTER);

        // 内容居中
        JPanel outerPanel = new JPanel(new GridBagLayout());
        outerPanel.setOpaque(false);
        outerPanel.add(content);

        setContentPane(outerPanel);
        pack();
        setLocationRelativeTo(parent);

        // 设置圆角窗口形状（防止系统边框显示方形）
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
    }

    /** 更新进度（不带百分比文字） */
    public void updateProgress(int value, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            if (message != null && !message.isEmpty()) {
                statusLabel.setText(message);
            }
        });
    }

    /** 完成后自动关闭 */
    public void finish() {
        SwingUtilities.invokeLater(this::dispose);
    }

    /** 模拟任务 */
    public void start() {

        // 任务执行进度
        Consumer<Integer> progress = i ->{
            updateProgress(i,"初始化...");
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
