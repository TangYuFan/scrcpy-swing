package com.tyf.demo.entity;

public class Device {

    String name;
    String type;

    String online;

    public Device(String name, String type, String online) {
        this.name = name;
        this.type = type;
        this.online = online;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOnline() {
        return online;
    }

    public void setOnline(String online) {
        this.online = online;
    }

    @Override
    public String toString() {
        return "Device{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", online='" + online + '\'' +
                '}';
    }
}
