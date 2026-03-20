package com.tyf.demo.entity;

import javax.swing.table.AbstractTableModel;
import java.util.List;

// 表格模型
public class DeviceTableModel extends AbstractTableModel {

    private final String[] columnNames = {"Name", "Type", "Action"};
    public final List<Device> devices;

    public DeviceTableModel(List<Device> devices) {
        this.devices = devices;
    }

    @Override
    public int getRowCount() {
        return devices.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Device device = devices.get(rowIndex);
        switch (columnIndex) {
            case 0: return device.name;
            case 1: return device.type;
            case 2: return "Open"; // 按钮文本
            default: return null;
        }
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 2; // 只有按钮列可编辑（点击）
    }
}