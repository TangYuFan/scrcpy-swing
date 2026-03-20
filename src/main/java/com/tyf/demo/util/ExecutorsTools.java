package com.tyf.demo.util;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *   @desc : 线程池工具类
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class ExecutorsTools {

    // socket
    public static ExecutorService initThread = Executors.newSingleThreadExecutor();

    // connect
    public static ExecutorService connectThread = Executors.newSingleThreadExecutor();

    public static void shutdown() {
        initThread.shutdown();
        connectThread.shutdown();
    }
}
