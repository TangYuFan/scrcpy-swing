package com.tyf.demo.util;

import com.tyf.demo.service.ConstService;
import org.pmw.tinylog.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 *   @desc : 对以及接入 usb 的设备进行自动连接 wifi 调试，连接成功后可以移除 usb 进行无线连接
 *   @auth : tyf
 *   @date : 2025-02-22 11:31:25
*/
public class WifiTools {

    private static String getAdbPath() {
        return ConstService.ADB_PATH + "adb.exe";
    }

    // 执行 cmd (使用默认 adb 路径)
    public static String cmd(String cmd) {
        return cmd(getAdbPath(), cmd);
    }

    // 执行 cmd (带 adb 路径)
    public static String cmd(String adbPath, String cmd) {
        StringBuilder res = new StringBuilder();
        try {
            List<String> cmdList = new ArrayList<>();
            cmdList.add(adbPath);
            for (String part : cmd.split(" ")) {
                if (!part.isEmpty()) {
                    cmdList.add(part);
                }
            }
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            InputStream in = process.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, Charset.forName("GBK")));
            String line;
            while ((line = br.readLine()) != null) {
                res.append(line).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString();
    }

    // 随机端口号
    public static String randomPort() {
        return ThreadLocalRandom.current().nextInt(1024, 65536) + "";
    }

    public static boolean isIpv4Port(String s) {
        if (s == null) return false;
        String[] parts = s.trim().split(":");
        if (parts.length != 2) return false;
        // port
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (port < 1 || port > 65535) return false;
        // ip
        String[] nums = parts[0].split("\\.");
        if (nums.length != 4) return false;
        for (String num : nums) {
            try {
                int n = Integer.parseInt(num);
                if (n < 0 || n > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }


    // 获取 adb usb 设备的 ip 地址
    public static String getIP(String name) {
        String adbPath = getAdbPath();
        String cmd = adbPath + " -s " + name + " shell ip route";
        String out = cmd(cmd);
        String ip = "";
        if (out.contains("src ")) {
            ip = out.substring(out.indexOf("src ") + 4).trim().split(" ")[0];
        }
        return ip;
    }


    // 自动获取 adb usb 连接设备的地址并创建无线调试
    public static void autoConncetIPV4() {
        String adbPath = getAdbPath();
        String names = cmd(adbPath, "devices");

        // 如果有多个设备，去掉 List of devices attached 获取多个设备
        List<String> devices = null;
        if (names != null) {
            String[] lines = names.split("\n");
            devices = Arrays.stream(lines).filter(n -> !n.contains("List of devices attached")).collect(Collectors.toList());
        }

        // 多种设备
        List<String> usbConnect = new ArrayList<>();
        List<String> wifiConnect = new ArrayList<>();
        List<String> wifiDisConnect = new ArrayList<>();

        if (devices != null && !devices.isEmpty()) {
            wifiDisConnect = devices.stream().map(n -> n.replace("device", "").trim()).filter(n -> isIpv4Port(n) && n.contains("offline")).collect(Collectors.toList());
            wifiConnect = devices.stream().map(n -> n.replace("device", "").trim()).filter(n -> isIpv4Port(n) && !n.contains("offline")).collect(Collectors.toList());
            usbConnect = devices.stream().map(n -> n.replace("device", "").trim()).filter(n -> !isIpv4Port(n)).collect(Collectors.toList());
        }

        Logger.info("------------------------------------------");
        Logger.info("USB Connect: " + Arrays.toString(usbConnect.toArray()));
        Logger.info("WIFI online: " + Arrays.toString(wifiConnect.toArray()));
        Logger.info("WIFI offline: " + Arrays.toString(wifiDisConnect.toArray()));

        // 遍历所有 USB 设备获取IP并对比是否已经有wifi连接
        if (!usbConnect.isEmpty()) {
            Logger.info("------------------------------------------");
            for (int i = 0; i < usbConnect.size(); i++) {
                String name = usbConnect.get(i);
                String ip = getIP(name);
                boolean hasConnect = !wifiConnect.stream().filter(n -> n.contains(ip)).collect(Collectors.toList()).isEmpty();
                if (!hasConnect) {
                    Logger.info("Device: " + name + ", IP: " + ip + ", Wifi Connect: " + hasConnect);
                    String port = randomPort();
                    String listen = adbPath + " -d tcpip " + port;
                    String connect = adbPath + " connect " + ip + ":" + port;
                    cmd(listen);
                    cmd(connect);
                }
            }
        }

        // 遍历所有离线设备进行断开
        if (!wifiDisConnect.isEmpty()) {
            for (int i = 0; i < wifiDisConnect.size(); i++) {
                String name = wifiDisConnect.get(i);
                String disconnectCmd = adbPath + " disconnect " + name;
                cmd(disconnectCmd);
            }
        }

        // 处理完毕最后查询一下所有设备
        Logger.info("------------------------------------------");
        Logger.info(cmd(adbPath, "devices"));
    }



    public static void main(String[] args) {

        autoConncetIPV4();

    }





}
