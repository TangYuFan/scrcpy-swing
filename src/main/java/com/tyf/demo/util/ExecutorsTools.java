package com.tyf.demo.util;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *   @desc : 所有线程池
 *   @auth : tyf
 *   @date : 2025-10-23 17:46:26
*/
public class ExecutorsTools {


    // socket
    public static ExecutorService initThread = Executors.newSingleThreadExecutor();

    // connect
    public static ExecutorService connectThread = Executors.newSingleThreadExecutor();



}
