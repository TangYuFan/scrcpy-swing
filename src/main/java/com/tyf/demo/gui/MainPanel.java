package com.tyf.demo.gui;

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
