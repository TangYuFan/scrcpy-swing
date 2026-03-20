package com.tyf.demo.util;


import com.tyf.demo.entity.Device;
import com.tyf.demo.service.ConstService;
import org.pmw.tinylog.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *   @desc : 设备工具类
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class DeviceTools {

    /**
     *   @desc : 查询所有设备
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static List<Device> listDevices(){

        String adb = ConstService.ADB_PATH + "adb.exe";
        String cmd =  adb + " devices";

        String rt = CmdTools.exec(cmd);
        Logger.info(rt);

        List<String> devices = null;
        if(rt!=null){
            String[] lines = rt.split("\n");
            devices = Arrays.stream(lines).filter(n->!n.contains("List of devices attached")).collect(Collectors.toList());
        }

        // 多种设备
        List<String> usbConnect = new ArrayList<>();// usb连接的设备
        List<String> wifiConnect = new ArrayList<>();// wifi连接的设备
        List<String> wifiDisConnect = new ArrayList<>();// wifi断开的设备

        if(devices!=null&&!devices.isEmpty()){
            wifiDisConnect = devices.stream().map(n-> n.replace("device","").trim()).filter(n->StringTools.isIpv4Port(n)&&n.contains("offline")).map(n->n.replace("offline","").replace("unauthorized","").trim()).collect(Collectors.toList());
            wifiConnect = devices.stream().map(n-> n.replace("device","").trim()).filter(n->StringTools.isIpv4Port(n)&&!n.contains("offline")).map(n->n.replace("offline","").replace("unauthorized","").trim()).collect(Collectors.toList());
            usbConnect = devices.stream().map(n-> n.replace("device","").trim()).filter(n->!StringTools.isIpv4Port(n)).map(n->n.replace("offline","").replace("unauthorized","").trim()).collect(Collectors.toList());
        }

//        System.out.println("------------------------------------------");
//        System.out.println("USB Connect："+Arrays.toString(usbConnect.toArray()));
//        System.out.println("WIFI online："+Arrays.toString(wifiConnect.toArray()));
//        System.out.println("WIFI offline："+Arrays.toString(wifiDisConnect.toArray()));


        List<Device> list = new ArrayList<>();
        usbConnect.stream().forEach(n->{
            list.add(new Device(n,"usb",""));
        });
        wifiConnect.stream().forEach(n->{
            list.add(new Device(n,"wifi","on"));
        });
        wifiDisConnect.stream().forEach(n->{
            list.add(new Device(n,"wifi","off"));
        });

        return list;
    }


}
