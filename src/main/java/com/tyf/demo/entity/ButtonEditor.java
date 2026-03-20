package com.tyf.demo.entity;

import javax.swing.*;
import java.awt.*;

/**
 *   @desc : 按钮编辑器
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class ButtonEditor extends DefaultCellEditor {
    private JButton button;
    private String label;
    private boolean clicked;
    private int row;
    private JTable table;

    private OnButtonClick clickListener;

    public ButtonEditor(JCheckBox checkBox, JTable table,OnButtonClick clickListener) {
        super(checkBox);
        this.table = table;
        this.clickListener = clickListener;
        button = new JButton();
        button.setOpaque(true);
        button.addActionListener(e -> fireEditingStopped());
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
    }

    /**
     *   @desc : 获取单元格编辑器组件
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int row, int column) {
        this.row = row;
        label = (value == null) ? "" : value.toString();
        button.setText(label);
        clicked = true;
        return button;
    }

    /**
     *   @desc : 获取单元格编辑器值
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public Object getCellEditorValue() {
        if (clicked) {
            // 获取设备对象
            DeviceTableModel model = (DeviceTableModel) table.getModel();
            Device device = model.devices.get(row);
            // 调用回调
            if (clickListener != null) {
                clickListener.onClick(device);
            }
        }
        clicked = false;
        return label;
    }

    /**
     *   @desc : 停止单元格编辑
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public boolean stopCellEditing() {
        clicked = false;
        return super.stopCellEditing();
    }


    /**
     *   @desc : 按钮点击回调接口
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public interface OnButtonClick {
        void onClick(Device device);
    }


}
