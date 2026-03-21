package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.util.GuiTools;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 *   @desc : 底部工具栏（显示超链接）
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
 */
public class BottomPanel extends JPanel {

    public BottomPanel() {

        setPreferredSize(new Dimension(0, 25));

        // 添加链接
        JLabel auth = GuiTools.createLinkLabelNoUnderline("v1.0.0 | auth©tangyufan", ConstService.FONT_NORMAL, ConstService.COLOR_BLACK);
        this.add(auth);

    }



    /**
     *   @desc : 创建工具按钮
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param text : 按钮文字
     *   @param action : 点击事件
     *   @return 按钮
     */
    private JButton createToolButton(String text, ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(ConstService.DEFAULT_FONT_NAME, Font.PLAIN, 11));
        btn.setFocusable(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(action);
        return btn;
    }

}