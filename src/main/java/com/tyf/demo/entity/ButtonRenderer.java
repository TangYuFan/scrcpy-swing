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
    public ButtonRenderer() {
        setOpaque(true);
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
        setText((value == null) ? "" : value.toString());
        return this;
    }
}