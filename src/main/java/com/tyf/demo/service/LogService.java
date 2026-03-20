package com.tyf.demo.service;


import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.writers.ConsoleWriter;
import com.tyf.demo.util.TimeTools;

import java.io.File;
import java.util.Arrays;

/**
 *   @desc : 日志工具类
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class LogService {


    // 当前日志文件
    public static String log_file = null;

    /**
     *   @desc : 创建日志目录
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void createLogDirectory(String directory){
        File d = new File(directory);
        System.out.println("日志文件目录："+d.getAbsolutePath());
        if(!d.exists()){
            d.mkdir();
        }
    }

    /**
     *   @desc : 创建日志文件
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void createLog(){
        File log = new File(log_file);
        System.out.println("日志文件路径："+log.getAbsolutePath());
        if(log.exists()){
            log.delete();
        }
        // 创建文件
        try {
            log.createNewFile();
        }
        catch (Exception e){
            e.printStackTrace();
            System.out.println("日志文件初始化失败");
            System.exit(0);
        }

        // 自动将 logging 输出到日志文件
        try {
            Configurator
                    // 设置为最低级别
                    .currentConfig().level(Level.TRACE)
                    .formatPattern("{date:yyyy-MM-dd HH:mm:ss} {level}: {message}")
                    // writer 子类有文件、滚动文件、jdbc、等等
                    .writer(new org.pmw.tinylog.writers.FileWriter(log_file))
                    .addWriter(new ConsoleWriter(System.out))
                    .activate();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }


    /**
     *   @desc : 默认清除三天以外的日志文件
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void clearLog(String directory){
        // 仅保留3天
        long old = 5;
        long limit = System.currentTimeMillis() - old * 24 * 60 * 60 * 1000;
        try {
            File dir = new File(directory);
            if(dir.exists()&&dir.isDirectory()){
                File[] files = dir.listFiles();
                Arrays.stream(files).forEach(n->{
                    // 转为时间戳
                    String name = n.getName().replace(".log","");
                    long t = TimeTools.timeStrToTimeStemp(name,11);
                    // old 之前的日志文件进行清除
                    if(t<=limit){
                        n.delete();
                    }
                });
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }



}
