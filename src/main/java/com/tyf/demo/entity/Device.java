package com.tyf.demo.entity;

/**
 *   @desc : 设备实体类
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class Device {

    String name;
    String type;

    String online;

    public Device(String name, String type, String online) {
        this.name = name;
        this.type = type;
        this.online = online;
    }

    /**
     *   @desc : 获取设备名称
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public String getName() {
        return name;
    }

    /**
     *   @desc : 设置设备名称
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *   @desc : 获取设备类型
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public String getType() {
        return type;
    }

    /**
     *   @desc : 设置设备类型
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void setType(String type) {
        this.type = type;
    }

    /**
     *   @desc : 获取在线状态
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public String getOnline() {
        return online;
    }

    /**
     *   @desc : 设置在线状态
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void setOnline(String online) {
        this.online = online;
    }

    /**
     *   @desc : 转换为字符串
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public String toString() {
        return "Device{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", online='" + online + '\'' +
                '}';
    }
}
