package com.tyf.demo.util;

public class StringTools {


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
