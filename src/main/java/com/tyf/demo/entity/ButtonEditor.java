package com.tyf.demo.entity;

import javax.swing.*;
import java.awt.*;

// 编辑按钮（处理点击事件）
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

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int row, int column) {
        this.row = row;
        label = (value == null) ? "" : value.toString();
        button.setText(label);
        clicked = true;
        return button;
    }

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

    @Override
    public boolean stopCellEditing() {
        clicked = false;
        return super.stopCellEditing();
    }


    // 定义点击回调接口
    public interface OnButtonClick {
        void onClick(Device device);
    }


}
