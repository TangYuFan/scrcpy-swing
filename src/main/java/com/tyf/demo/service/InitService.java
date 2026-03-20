package com.tyf.demo.service;

import com.tyf.demo.util.ExecutorsTools;
import com.tyf.demo.util.TimeTools;
import org.pmw.tinylog.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 *   @desc : 应用初始化
 *   @auth : tyf
 *   @date : 2025-12-03 14:49:36
*/
public class InitService {


    // 工作目录初始化
    public static void initWorkSpace(){
        File wk = new File(ConstService.WORKSPACE);
        System.out.println("工作目录地址："+wk.getAbsolutePath());
        if(!wk.exists()){
            wk.mkdirs();
        }
    }


    // 日志初始化
    public static void initLog(){
        // 初始化日志文件,清除3天以外的日志文件
        LogService.log_file = ConstService.LOG_DIR + TimeTools.timeStempToTimeStr(System.currentTimeMillis(),11) +".log";
        LogService.createLogDirectory(ConstService.LOG_DIR);
        LogService.clearLog(ConstService.LOG_DIR);
        LogService.createLog();
    }


    // dex 文件初始化
    public static void initDex(){
        // deprecated: old dex-based mobile_demo pipeline removed (use scrcpy-server.jar instead)
    }

    // scrcpy-server.jar 初始化
    public static void initScrcpyServer() {
        try {
            ScrcpyService.initLocalServerJar();
        } catch (Exception e) {
            Logger.error("scrcpy server init fail: " + e.getMessage());
        }
    }

    // adb 命令行初始化
    public static void initAdb() {
        // 避免并发重复解压
        synchronized (InitService.class) {
            doInitAdbUnlocked();
        }
    }

    /**
     * 将 jar 内 /adb/* 解压到工作目录。
     * Windows 上若先 delete 再写，而 adb.exe 仍被本应用或其它进程占用，会报「另一个程序正在使用此文件」并导致初始化失败。
     * 策略：已存在且非空则跳过；仅缺失时写入；写入失败时若已有旧文件则继续用旧文件。
     */
    private static void doInitAdbUnlocked() {
        String adbPath = ConstService.ADB_PATH;
        File adbDir = new File(adbPath);

        if (!adbDir.exists() && !adbDir.mkdirs()) {
            Logger.error("无法创建 adb 目录: " + adbDir.getAbsolutePath());
            return;
        }

        List<String> adbFiles = new ArrayList<>();
        adbFiles.add("adb.exe");
        adbFiles.add("AdbWinApi.dll");
        adbFiles.add("AdbWinUsbApi.dll");

        for (String fileName : adbFiles) {
            File targetFile = new File(adbDir, fileName);
            if (targetFile.isFile() && targetFile.length() > 0L) {
                Logger.info("adb 已存在，跳过解压: " + targetFile.getAbsolutePath());
                continue;
            }

            try (InputStream in = InitService.class.getResourceAsStream("/adb/" + fileName)) {
                if (in == null) {
                    Logger.error("缺少文件：/adb/" + fileName);
                    continue;
                }
                File tmp = new File(adbDir, fileName + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                }
                try {
                    Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Logger.info("File Transfer：" + targetFile.getAbsolutePath());
                } catch (Exception moveEx) {
                    try {
                        Files.deleteIfExists(tmp.toPath());
                    } catch (Exception ignore) {
                        tmp.deleteOnExit();
                    }
                    if (targetFile.isFile() && targetFile.length() > 0L) {
                        Logger.warn("无法覆盖正在使用的 " + fileName + "，沿用已有文件 — " + moveEx.getMessage());
                    } else {
                        throw moveEx;
                    }
                }
            } catch (Exception e) {
                if (targetFile.isFile() && targetFile.length() > 0L) {
                    Logger.warn("解压 adb 资源失败，使用已存在文件: " + targetFile.getAbsolutePath() + " — " + e.getMessage());
                } else {
                    Logger.error("解压 adb 失败: " + fileName + " — " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }


    // 开始启动初始化
    public static void init(Consumer<Integer> progress,Consumer<Void> finish){

        ExecutorsTools.initThread.execute(()->{
            // 初始化工作目录
            initWorkSpace();
            // 初始化日志
            initLog();
            // 初始化dex文件
            // initDex(); // removed
            // adb 命令行初始化
            initAdb();
            // scrcpy-server 初始化
            initScrcpyServer();
        });

        // 模拟进度
        try {
            for (int i = 0; i < 100; i++) {
                progress.accept(i);
                Thread.sleep(5);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        // 执行完成调用这个
        finish.accept(null);

    }

}
