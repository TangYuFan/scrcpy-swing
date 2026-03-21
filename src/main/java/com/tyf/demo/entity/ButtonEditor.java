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

    /** 绿色文字 - Open按钮 */
    private static final Color COLOR_GREEN = new Color(34, 139, 34);
    /** 红色文字 - Close按钮 */
    private static final Color COLOR_RED = new Color(220, 20, 60);

    public ButtonEditor(JCheckBox checkBox, JTable table, OnButtonClick clickListener) {
        super(checkBox);
        this.table = table;
        this.clickListener = clickListener;
        button = new JButton();
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        button.addActionListener(e -> fireEditingStopped());
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
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

        // 根据按钮文本设置文字颜色
        if ("Open".equals(label)) {
            button.setForeground(COLOR_GREEN);
        } else if ("Close".equals(label)) {
            button.setForeground(COLOR_RED);
        }

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
            // 判断设备是否已连接
            boolean isConnected = model.isConnected(device);
            // 调用回调，传入设备和连接状态
            if (clickListener != null) {
                clickListener.onClick(device, isConnected);
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
        /**
         *   @desc : 按钮点击回调
         *   @auth : tyf
         *   @date : 2026-03-21
         *   @param device : 点击的设备
         *   @param isConnected : 设备是否已连接
        */
        void onClick(Device device, boolean isConnected);
    }


}
