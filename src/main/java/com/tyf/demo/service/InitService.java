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
 *   @date : 2026-03-20 14:04:14
*/
public class InitService {


    /**
     *   @desc : 工作目录初始化
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void initWorkSpace(){
        File wk = new File(ConstService.WORKSPACE);
        System.out.println("Workspace: " + wk.getAbsolutePath());
        if(!wk.exists()){
            wk.mkdirs();
        }
    }


    /**
     *   @desc : 日志初始化
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void initLog(){
        // 初始化日志文件,清除3天以外的日志文件
        LogService.log_file = ConstService.LOG_DIR + TimeTools.timeStempToTimeStr(System.currentTimeMillis(),11) +".log";
        LogService.createLogDirectory(ConstService.LOG_DIR);
        LogService.clearLog(ConstService.LOG_DIR);
        LogService.createLog();
    }


    /**
     *   @desc : scrcpy-server.jar 初始化
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void initScrcpyServer() {
        try {
            ScrcpyService.initLocalServerJar();
        } catch (Exception e) {
            Logger.error("scrcpy server init fail: " + e.getMessage());
        }
    }

    /**
     *   @desc : adb 命令行初始化
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void initAdb() {
        // 避免并发重复解压
        synchronized (InitService.class) {
            String adbPath = ConstService.ADB_PATH;
            File adbDir = new File(adbPath);

            if (!adbDir.exists() && !adbDir.mkdirs()) {
                Logger.error("Failed to create adb dir: " + adbDir.getAbsolutePath());
                return;
            }

            List<String> adbFiles = new ArrayList<>();
            adbFiles.add("adb.exe");
            adbFiles.add("AdbWinApi.dll");
            adbFiles.add("AdbWinUsbApi.dll");

            for (String fileName : adbFiles) {
                File targetFile = new File(adbDir, fileName);
                if (targetFile.isFile() && targetFile.length() > 0L) {
                    Logger.info("File Exist: " + fileName);
                    continue;
                }
                try (InputStream in = InitService.class.getResourceAsStream("/adb/" + fileName)) {
                    if (in == null) {
                        Logger.error("Missing resource: /adb/" + fileName);
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
                        Logger.info("File Transfer: " + targetFile.getAbsolutePath());
                    } catch (Exception moveEx) {
                        try {
                            Files.deleteIfExists(tmp.toPath());
                        } catch (Exception ignore) {
                            tmp.deleteOnExit();
                        }
                        if (targetFile.isFile() && targetFile.length() > 0L) {
                            Logger.warn("In use, using existing: " + fileName);
                        } else {
                            throw moveEx;
                        }
                    }
                } catch (Exception e) {
                    if (targetFile.isFile() && targetFile.length() > 0L) {
                            Logger.warn("Extract failed, using existing: " + targetFile.getAbsolutePath());
                    } else {
                        Logger.error("Extract failed: " + fileName);
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     *   @desc : 开始启动初始化
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void init(Consumer<Integer> progress,Consumer<Void> finish){

        ExecutorsTools.initThread.execute(()->{
            // 初始化工作目录
            initWorkSpace();
            // 初始化日志
            initLog();
            // adb命令行初始化
            initAdb();
            // scrcpy-server初始化到本地
            initScrcpyServer();
            // 游戏映射配置初始化（内置 FPS 映射 + 合并本地配置）
            GameMappingConfig.loadMappings();
            GameMappingConfig.ensureBuiltinMappings();
            GameMappingConfig.saveMappings();
        });

        // 进度 TODO
        try {
            for (int i = 0; i < 100; i++) {
                progress.accept(i);
                Thread.sleep(2);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }

        // 执行完成调用这个
        finish.accept(null);

    }

}
