package com.tyf.demo.entity;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 *   @desc : 按钮渲染器
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
 */
public class ButtonRenderer extends JButton implements TableCellRenderer {

    /** 蓝色文字 - Open按钮 */
    private static final Color COLOR_BLUE = new Color(30, 136, 229);
    /** 红色文字 - Close按钮 */
    private static final Color COLOR_RED = new Color(220, 20, 60);

    public ButtonRenderer() {
        setOpaque(true);
        setBorderPainted(true);
        setFont(new Font(Font.DIALOG, Font.BOLD, 12));
    }

    /**
     *   @desc : 获取单元格渲染组件
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        String text = (value == null) ? "" : value.toString();
        setText(text);

        // 根据按钮文本设置文字颜色
        if ("Open".equals(text)) {
            setForeground(COLOR_BLUE);
        } else if ("Close".equals(text)) {
            setForeground(COLOR_RED);
        } else {
            setForeground(UIManager.getColor("Button.foreground"));
        }

        return this;
    }
}