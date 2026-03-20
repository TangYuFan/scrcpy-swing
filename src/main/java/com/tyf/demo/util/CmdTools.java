package com.tyf.demo.util;

import org.pmw.tinylog.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class CmdTools {

    /**
     *   @desc : 执行系统命令
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static String exec(String cmd) {
        StringBuilder result = new StringBuilder();
        Process process = null;
        try {
            // 执行命令
            process = Runtime.getRuntime().exec(cmd);
            // 读取正常输出（GBK 解决 Windows 中文乱码）
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }
            // 读取错误输出
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream(), "GBK"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }
            // 等待命令执行完成
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            result.append("执行异常：").append(e.getMessage());
            e.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result.toString();
    }


    /**
     *   @desc : 启动后台进程
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static Process startBackgroundProcess(String cmd) throws IOException {
        Process mobileProcess = Runtime.getRuntime().exec(cmd);
        drainLinesAsync(mobileProcess.getInputStream(), false, "adb-bg-out");
        drainLinesAsync(mobileProcess.getErrorStream(), true, "adb-bg-err");
        return mobileProcess;
    }

    private static void drainLinesAsync(InputStream stream, boolean error, String threadBaseName) {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (error) {
                        Logger.error("[Mobile][ERR] " + line);
                    } else {
                        Logger.info("[Mobile] " + line);
                    }
                }
            } catch (Exception e) {
                if (!error) {
                    Logger.info("[Mobile] stream end / shutdown");
                }
            }
        }, threadBaseName).start();
    }

    /** @deprecated 使用 {@link #startBackgroundProcess(String)} 并保存 {@link Process}，否则无法在退出时释放 adb。 */
    @Deprecated
    public static void startProcess(String cmd) {
        try {
            startBackgroundProcess(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *   @desc : 启动进程并监听关键字
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void startProcess(String cmd, String exitKey, String successKey, Consumer<Boolean> callback){
        try {
            // 启动进程，并保存到静态变量
            Process mobileProcess = Runtime.getRuntime().exec(cmd);
            Process finalProcess = mobileProcess;
            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(mobileProcess.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        Logger.info("[Mobile] " + line);
                        // 如果进程输出退出关键字则主动关闭当前进程
                        if (exitKey != null && line.contains(exitKey)) {
                            Logger.info("Process Get ExitCode："+exitKey);
                            finalProcess.destroy();
                            break;
                        }
                        // 如果进程输出启动成功关键字则通知PC端
                        if(successKey!=null && line.contains(successKey)){
                            Logger.info("Process Start Success");
                            callback.accept(true);
                        }
                    }
                    Logger.info("Process Exit");
                    callback.accept(false);
                } catch (Exception e) {
                    // 进程被停止会走到这里
                    Logger.info("Process Exception Shutdown");
                    callback.accept(false);
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
            callback.accept(false);
        }
    }


    /**
     *   @desc : 同步启动进程
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void startProcessSync(String cmd,String exitKey){
        try {
            // 启动进程，并保存到静态变量
            Process mobileProcess = Runtime.getRuntime().exec(cmd);
            Process finalProcess = mobileProcess;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(mobileProcess.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    Logger.info("[Mobile] " + line);
                    // 如果进程输出退出关键字则主动关闭当前进程
                    if (exitKey != null && line.contains(exitKey)) {
                        Logger.info("Process Get ExitCode："+exitKey);
                        finalProcess.destroy();
                        break;
                    }
                }
                Logger.info("Process Shutdown");
            } catch (Exception e) {
                // 进程被停止会走到这里
                Logger.info("Process Exception Shutdown");
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}