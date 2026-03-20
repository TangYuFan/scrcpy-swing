package com.tyf.demo.util;

/**
 *   @desc : 字符串工具类
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class StringTools {

    /**
     *   @desc : 判断是否为IPv4地址格式
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
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

}
