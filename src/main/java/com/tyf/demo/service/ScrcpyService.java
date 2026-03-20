package com.tyf.demo.service;

import com.tyf.demo.entity.Device;
import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.util.CmdTools;
import com.tyf.demo.util.ExecutorsTools;
import org.pmw.tinylog.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 *   @desc : Scrcpy 服务端运行器
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public final class ScrcpyService {

    /** 标记 scrcpy 是否正在运行 */
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean decoderRunning = new AtomicBoolean(false);

    /** 长时间运行的「adb shell … app_process」子进程，必须在退出时 destroy，否则会占用 adb.exe。 */
    private static final AtomicReference<Process> scrcpyAdbShell = new AtomicReference<>();
    /** 当前 socket 连接，用于 shutdown 时强制关闭 */
    private static final AtomicReference<Socket> currentSocket = new AtomicReference<>();
    /** 当前 packet reader，用于 shutdown 时关闭流 */
    private static final AtomicReference<ScrcpyVideoPacketReader> currentReader = new AtomicReference<>();
    /** 当前解码线程，用于 shutdown 时等待其结束 */
    private static final AtomicReference<Thread> decodeThread = new AtomicReference<>();

    private static volatile String activeDeviceId;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ScrcpyService::shutdown, "scrcpy-adb-cleanup"));
    }

    private ScrcpyService() {}

    /**
     *   @desc : 初始化本地 scrcpy server jar
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static File initLocalServerJar() throws Exception {
        return extractServerJar(true);
    }

    /**
     *   @desc : 启动 scrcpy 投屏服务
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void start(Device device) throws Exception {
        if (!running.compareAndSet(false, true)) {
            Logger.info("scrcpy 已在运行中，跳过启动");
            return;
        }

        boolean started = false;
        String deviceId = device.getName();
        try {
            activeDeviceId = deviceId;

            // 步骤 1：确保本地 scrcpy-server.jar 已存在（若不存在则从 resources 提取）
            File localJar = extractServerJar(false);

            // 步骤 1.5：清理手机上残留的 scrcpy server 进程
            adbQuiet(deviceId, "shell am force-stop com.genymobile.scrcpy");
            Thread.sleep(200);

            // 步骤 2：将 scrcpy-server.jar 推送到手机（带重试）
            int pushRetries = 3;
            for (int i = 0; i < pushRetries; i++) {
                try {
                    adb(deviceId, "push \"" + localJar.getAbsolutePath() + "\" " + ConstService.SCRCPY_DEVICE_SERVER_PATH);
                    break;
                } catch (Exception e) {
                    if (i == pushRetries - 1) throw e;
                    Logger.info("adb push 失败，重试 " + (i + 1) + "/" + pushRetries);
                    Thread.sleep(1000);
                }
            }

            // 步骤 3：建立 adb 端口转发
            // 将本地 TCP 端口转发到手机的 localabstract:scrcpy
            // tunnel_forward=true 表示手机监听连接，PC 主动连接
            // 先移除旧的转发，避免半开连接或错误的对端
            adbQuiet(deviceId, "forward --remove tcp:" + ConstService.SCRCPY_VIDEO_FORWARD_PORT);
            adb(deviceId, "forward tcp:" + ConstService.SCRCPY_VIDEO_FORWARD_PORT + " localabstract:scrcpy");

            // 步骤 3.5：杀死手机上可能还在运行的 scrcpy-server
            adbQuiet(deviceId, "shell am force-stop com.genymobile.scrcpy");
            Thread.sleep(200);

            // 步骤 4：启动手机上的 scrcpy-server（后台异步执行）
            // 命令格式：shell CLASSPATH=<jar路径> app_process / <启动类>
            // 参数说明：
            //   log_level=info       - 日志级别
            //   video=true           - 启用视频流
            //   audio=false          - 禁用音频
            //   control=false        - 禁用控制（仅视频）
            //   tunnel_forward=true  - 隧道模式：手机创建 LocalServerSocket 监听
            //   raw_stream=false     - 使用 scrcpy 协议封装
            //   send_device_meta=true    - 发送设备信息
            //   send_codec_meta=true     - 发送编码器信息
            //   send_frame_meta=true     - 发送帧元数据
            //   send_dummy_byte=true     - 发送占位字节
            //   max_fps=60           - 最大帧率
            //   max_size=1280        - 最大分辨率边长
            String startCmd =
                    "shell CLASSPATH=" + ConstService.SCRCPY_DEVICE_SERVER_PATH + " app_process / com.genymobile.scrcpy.Server " + ConstService.SCRCPY_SERVER_VERSION + " " +
                            "log_level=info " +
                            "video=true audio=false control=false " +
                            "tunnel_forward=true " +
                            "raw_stream=false " +
                            "send_device_meta=true send_codec_meta=true send_frame_meta=true send_dummy_byte=true " +
                            "max_fps=" + ConstService.SCRCPY_MAX_FPS +
                            (ConstService.SCRCPY_MAX_SIZE > 0 ? " max_size=" + ConstService.SCRCPY_MAX_SIZE : "");
            String fullStartCmd = adbCmd(deviceId) + " " + startCmd;
            Logger.info("启动 scrcpy-server 命令: " + fullStartCmd);

            // 后台启动 adb shell 进程（保持运行，接收 server 日志）
            Process shell = CmdTools.startBackgroundProcess(fullStartCmd);
            scrcpyAdbShell.set(shell);

            // 步骤 5：启动视频解码线程（异步）
            // 该线程会尝试连接 socket，接收并解码视频流
            startDecodeThread();
            started = true;
        } catch (Exception e) {
            // 发生异常时，清理资源
            adbQuiet(deviceId, "forward --remove tcp:" + ConstService.SCRCPY_VIDEO_FORWARD_PORT);
            Process p = scrcpyAdbShell.getAndSet(null);
            destroyQuietly(p);
            activeDeviceId = null;
            throw e;
        } finally {
            if (!started) {
                running.set(false);
            }
        }
    }

    /**
     *   @desc : 关闭 scrcpy 投屏服务
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void shutdown() {
        // 1. 销毁 adb shell 子进程
        Process p = scrcpyAdbShell.getAndSet(null);
        destroyQuietly(p);

        // 2. 强制关闭 socket（打断 decodeLoop 的阻塞读取）
        Socket sock = currentSocket.getAndSet(null);
        if (sock != null) {
            try {
                sock.close();
            } catch (Exception ignore) {}
        }

        // 3. 关闭 packet reader
        ScrcpyVideoPacketReader r = currentReader.getAndSet(null);
        if (r != null) {
            try {
                r.close();
            } catch (Exception ignore) {}
        }

        // 4. 等待解码线程结束，防止重连时线程竞争导致闪退
        Thread t = decodeThread.getAndSet(null);
        if (t != null) {
            try {
                t.join(3000);
                if (t.isAlive()) {
                    Logger.warn("scrcpy decode thread did not exit in time, interrupting");
                    t.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.warn("scrcpy shutdown interrupted");
            }
        }

        // 5. 移除端口转发
        String deviceId = activeDeviceId;
        activeDeviceId = null;
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            adbQuiet(deviceId, "forward --remove tcp:" + ConstService.SCRCPY_VIDEO_FORWARD_PORT);
        }

        // 6. 关闭线程池
        ExecutorsTools.shutdown();

        running.set(false);
    }

    private static void destroyQuietly(Process p) {
        if (p == null) {
            return;
        }
        try {
            p.destroy();
            if (!waitForExit(p, 3000)) {
                p.destroyForcibly();
                waitForExit(p, 2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                p.destroyForcibly();
            } catch (Exception ignore) {}
        } catch (Exception ignore) {}
    }

    /** Java 8 无 {@code Process.waitFor(timeout)}，用手动轮询。 */
    private static boolean waitForExit(Process p, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                p.exitValue();
                return true;
            } catch (IllegalThreadStateException stillRunning) {
                Thread.sleep(50);
            }
        }
        try {
            p.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            return false;
        }
    }

    /**
     *   @desc : 启动解码线程
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static void startDecodeThread() {
        if (!decoderRunning.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                decodeLoop();
            } catch (Throwable e) {
                Logger.error("scrcpy decode thread stopped: " + e);
                e.printStackTrace();
            } finally {
                decoderRunning.set(false);
                running.set(false);
                decodeThread.set(null);
            }
        }, "scrcpy-decode");
        decodeThread.set(t);
        t.start();
    }

    /**
     *   @desc : 解码循环
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static void decodeLoop() throws Exception {
        // 等待手机上的 scrcpy-server 启动完成
        Thread.sleep(400);

        final int maxAttempts = 50;   // 最多重试 50 次
        final int retryGapMs = 200;   // 每次重试间隔 200ms
        Socket s = null;
        ScrcpyVideoPacketReader reader = null;
        IOException lastFail = null;
        boolean headerOk = false;

        // 步骤 1：连接 socket（带重试）
        // 尝试连接本地转发的端口，直到成功读取视频头
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {}
                reader = null;
            }
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignore) {}
                s = null;
            }
            try {
                Logger.info("scrcpy 视频连接尝试 " + attempt + "/" + maxAttempts + " -> 127.0.0.1:" + ConstService.SCRCPY_VIDEO_FORWARD_PORT);
                s = new Socket();
                s.connect(new InetSocketAddress("127.0.0.1", ConstService.SCRCPY_VIDEO_FORWARD_PORT), 5000);
                s.setTcpNoDelay(true);  // 禁用 Nagle 算法，降低延迟
                s.setSoTimeout(0);     // 无超时（阻塞模式读取）
                reader = new ScrcpyVideoPacketReader(s.getInputStream());
                reader.readHeader();    // 读取视频头（分辨率、编码器信息等）
                headerOk = true;
                break;
            } catch (IOException e) {
                lastFail = e;
                Logger.info("scrcpy: 等待手机 accept / 读取视频头 (" + e.getMessage() + ")");
                Thread.sleep(retryGapMs);
            }
        }

        // 记录 socket 和 reader 引用，供 shutdown() 使用
        currentSocket.set(s);
        currentReader.set(reader);

        // 连接失败，抛出异常
        if (!headerOk) {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {}
            }
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignore) {}
            }
            throw new IOException("scrcpy: 在 " + maxAttempts + " 次尝试后仍无法读取视频头", lastFail);
        }

        // 步骤 2：获取视频分辨率
        final int vw = reader.getWidth();
        final int vh = reader.getHeight();
        Logger.info("scrcpy: 视频分辨率 " + vw + "x" + vh + "（JavaCV/FFmpeg 进程内解码）");

        // 步骤 3：视频解码主循环
        // 从 socket 读取 H.264 数据包 → 使用 FFmpeg 解码为原始图像 → 绘制到 UI
        try (Socket sock = s) {
            try (ScrcpyVideoPacketReader r = reader) {
                AtomicLong decodedFrames = new AtomicLong();
                ScrcpyFrameSink sink = (packed, w, h) -> {
                    long n = decodedFrames.incrementAndGet();
                    int len = packed == null ? 0 : packed.length;
                    int expect = w * h * 3;  // BGR 格式，每像素 3 字节

                    // 抽样日志：前 5 帧 + 每隔 120 帧（避免刷屏）
                    if (n <= 5 || n % 120 == 0) {
                        int b0 = (packed != null && len > 0) ? (packed[0] & 0xff) : -1;
                        int bLast = (packed != null && len > 1) ? (packed[len - 1] & 0xff) : -1;
                        Logger.info("scrcpy: 解码成功 frame#" + n + " " + w + "x" + h
                                + " bytes=" + len + (len == expect ? "" : " (预期 " + expect + ")")
                                + " b[0]=" + b0 + " b[last]=" + bLast);
                    }
                    if (ConstService.SCRCPY_DRAW_DECODED_TO_UI && MainPanel.getContentPanel() != null) {
                        MainPanel.getContentPanel().postFramePackedBgr(packed, w, h);
                    }
                };

                try (ScrcpyH264Decoder dec = new ScrcpyH264Decoder(vw, vh)) {
                    long accessUnits = 0;
                    while (true) {
                        try {
                            // 读取下一个 H.264 数据包
                            byte[] au = r.nextMediaPacket();
                            accessUnits++;
                            boolean sample = (accessUnits <= 5 || accessUnits % 120 == 0);
                            if (sample) {
                                Logger.info("scrcpy: access-units=" + accessUnits + " auBytes=" + (au == null ? 0 : au.length));
                            }

                            // 计时：记录解码耗时
                            long t0 = sample ? System.nanoTime() : 0;

                            // 解码 H.264 数据包
                            dec.decode(au, sink);

                            if (sample) {
                                long dtMs = (System.nanoTime() - t0) / 1_000_000L;
                                Logger.info("scrcpy: 解码 AU#" + accessUnits + " 耗时=" + dtMs + "ms");
                            }
                        } catch (IOException e) {
                            // 流关闭或读取错误，正常退出循环
                            Logger.info("scrcpy: 流关闭或读取错误 — " + e.getMessage());
                            break;
                        } catch (Throwable t) {
                            Logger.error("scrcpy: 解码循环异常 — " + t);
                            t.printStackTrace();
                            break;
                        }
                    }
                }
            }
            // 清理引用
            currentSocket.set(null);
            currentReader.set(null);
        }
    }

    /**
     *   @desc : 提取 scrcpy-server.jar
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static File extractServerJar(boolean forceRewrite) throws Exception {
        File dir = new File(ConstService.WORKSPACE, "scrcpy");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File out = new File(dir, ConstService.SCRCPY_SERVER_JAR_NAME);

        // 文件已存在且不强制重写，直接返回
        if (out.exists() && !forceRewrite) {
            Logger.info("scrcpy server jar 已存在: " + out.getAbsolutePath());
            return out;
        }

        // 从 resources 读取并写入本地文件
        try (InputStream in = ScrcpyService.class.getResourceAsStream(ConstService.SCRCPY_SERVER_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("缺少资源文件: " + ConstService.SCRCPY_SERVER_RESOURCE);
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
        }
        Logger.info("scrcpy server jar 已提取: " + out.getAbsolutePath());
        return out;
    }

    /**
     *   @desc : 执行 adb 命令（带日志记录）
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static void adb(String deviceId, String args) {
        String cmd = adbCmd(deviceId) + " " + args;
        Logger.info(cmd);
        String rt = CmdTools.exec(cmd);
        if (rt != null && !rt.trim().isEmpty()) {
            Logger.info(rt);
        }
    }

    /**
     *   @desc : 执行 adb 命令（静默模式）
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static void adbQuiet(String deviceId, String args) {
        String cmd = adbCmd(deviceId) + " " + args;
        CmdTools.exec(cmd);
    }

    /**
     *   @desc : 构造 adb 命令
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static String adbCmd(String deviceId) {
        String adb = ConstService.ADB_PATH + "adb.exe";
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return adb;
        }
        return adb + " -s " + deviceId;
    }
}

