package com.tyf.demo.entity;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 *   @desc : 设备表格模型
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
 */
public class DeviceTableModel extends AbstractTableModel {

    private final String[] columnNames = {"Name", "Type", "Action"};
    public final List<Device> devices;
    /** 当前已连接的设备ID */
    public String connectedDeviceId;

    public DeviceTableModel(List<Device> devices, String connectedDeviceId) {
        this.devices = devices;
        this.connectedDeviceId = connectedDeviceId;
    }

    /**
     *   @desc : 获取行数
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public int getRowCount() {
        return devices.size();
    }

    /**
     *   @desc : 获取列数
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    /**
     *   @desc : 获取单元格值
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Device device = devices.get(rowIndex);
        switch (columnIndex) {
            case 0: return device.name;
            case 1: return device.type;
            case 2: return isConnected(device) ? "Close" : "Open"; // 根据连接状态返回按钮文本
            default: return null;
        }
    }

    /**
     *   @desc : 判断设备是否已连接
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public boolean isConnected(Device device) {
        return connectedDeviceId != null && connectedDeviceId.equals(device.name);
    }

    /**
     *   @desc : 获取列名
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    /**
     *   @desc : 判断单元格是否可编辑
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 2; // 只有按钮列可编辑（点击）
    }
}