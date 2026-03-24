package com.tyf.demo.service;

import com.tyf.demo.entity.Device;
import com.tyf.demo.gui.MainFrame;
import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.gui.ToolWindow;
import com.tyf.demo.util.CmdTools;
import org.pmw.tinylog.Logger;

import javax.swing.*;
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
    /** 当前 video socket 连接，用于 shutdown 时强制关闭 */
    private static final AtomicReference<Socket> currentSocket = new AtomicReference<>();
    /** 当前 control socket 连接（包级私有，供 ControlService 访问） */
    static final AtomicReference<Socket> controlSocket = new AtomicReference<>();
    /** 当前 packet reader，用于 shutdown 时关闭流 */
    private static final AtomicReference<ScrcpyVideoPacketReader> currentReader = new AtomicReference<>();
    /** 当前解码线程，用于 shutdown 时等待其结束 */
    private static final AtomicReference<Thread> decodeThread = new AtomicReference<>();

    private static volatile String activeDeviceId;

    /** 断开监听器，用于通知上层连接异常断开 */
    private static volatile OnDisconnectListener disconnectListener;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ScrcpyService::shutdown, "scrcpy-adb-cleanup"));
    }

    private ScrcpyService() {}

    /**
     *   @desc : 设置断开监听器
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param listener : 断开监听器
     */
    public static void setOnDisconnectListener(OnDisconnectListener listener) {
        ScrcpyService.disconnectListener = listener;
    }

    /**
     *   @desc : 断开监听器接口
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    public interface OnDisconnectListener {
        /**
         *   @desc : 连接异常断开时的回调
         *   @auth : tyf
         *   @date : 2026-03-21
         *   @param reason : 断开原因描述
         */
        void onDisconnect(String reason);
    }

    /**
     *   @desc : 获取当前已连接设备的ID
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @return 当前设备ID，未连接则返回null
     */
    public static String getActiveDeviceId() {
        return activeDeviceId;
    }

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
            Logger.info("scrcpy already running, skip");
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
                    Logger.info("adb push failed, retry " + (i + 1) + "/" + pushRetries);
                    Thread.sleep(1000);
                }
            }

            // 步骤 3：建立 adb 端口转发
            // 将本地 TCP 端口转发到手机的 localabstract:scrcpy
            // tunnel_forward=true 表示手机监听连接，PC 主动连接
            // 先移除旧的转发，避免半开连接或错误的对端
            adbQuiet(deviceId, "forward --remove tcp:" + ConstService.SCRCPY_PORT);
            adb(deviceId, "forward tcp:" + ConstService.SCRCPY_PORT + " localabstract:scrcpy");

            // 步骤 3.5：杀死手机上可能还在运行的 scrcpy-server
            adbQuiet(deviceId, "shell am force-stop com.genymobile.scrcpy");
            Thread.sleep(200);

            // 步骤 4：启动手机上的 scrcpy-server（后台异步执行）
            // 命令格式：shell CLASSPATH=<jar路径> app_process / <启动类>
            // 关键参数说明：
            //   log_level=info       - 日志级别
            //   video=true           - 启用视频流
            //   audio=false          - 禁用音频
            //   control=true         - 启用控制（必须！否则无法发送触摸/键盘事件）
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
                            "video=true " +
                            "audio=false " +
                            "control=true " +   // 【关键】启用控制！
                            "tunnel_forward=true " +
                            "raw_stream=false " +
                            "send_device_meta=true send_codec_meta=true send_frame_meta=true send_dummy_byte=true " +
                            "max_fps=" + ConstService.SCRCPY_MAX_FPS +
                            (ConstService.SCRCPY_MAX_SIZE > 0 ? " max_size=" + ConstService.SCRCPY_MAX_SIZE : "");
            String fullStartCmd = adbCmd(deviceId) + " " + startCmd;
            Logger.info("Start scrcpy-server cmd: " + fullStartCmd);

            // 后台启动 adb shell 进程（保持运行，接收 server 日志）
            Process shell = CmdTools.startBackgroundProcess(fullStartCmd);
            scrcpyAdbShell.set(shell);

            // 步骤 5：启动视频解码线程（异步）
            // 该线程会尝试连接 socket，接收并解码视频流
            startDecodeThread();

            // 更新窗口标题为 "Mobile - 设备名称"
            MainFrame.updateTitle(deviceId);

            started = true;
        } catch (Exception e) {
            // 发生异常时，清理资源
            adbQuiet(deviceId, "forward --remove tcp:" + ConstService.SCRCPY_PORT);
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
        // 1. 关闭控制连接
        Socket ctrlSock = controlSocket.getAndSet(null);
        if (ctrlSock != null) {
            try {
                ctrlSock.close();
            } catch (Exception ignore) {}
        }

        // 2. 销毁 adb shell 子进程
        Process p = scrcpyAdbShell.getAndSet(null);
        destroyQuietly(p);

        // 3. 强制关闭 video socket（打断 decodeLoop 的阻塞读取）
        Socket sock = currentSocket.getAndSet(null);
        if (sock != null) {
            try {
                sock.close();
            } catch (Exception ignore) {}
        }

        // 4. 关闭 packet reader
        ScrcpyVideoPacketReader r = currentReader.getAndSet(null);
        if (r != null) {
            try {
                r.close();
            } catch (Exception ignore) {}
        }

        // 5. 等待解码线程结束，防止重连时线程竞争导致闪退
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

        // 6. 移除端口转发
        String deviceId = activeDeviceId;
        activeDeviceId = null;
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            adbQuiet(deviceId, "forward --remove tcp:" + ConstService.SCRCPY_PORT);
        }

        // 7. 恢复默认窗口标题
        MainFrame.updateTitle(null);

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
     *   @desc : 连接控制 Socket
     *   @auth : tyf
     *   @date : 2026-03-21
     *   说明：视频和控制共用同一端口，需要连接两次。
     *         第1次连接成功建立视频通道，
     *         第2次连接成功建立控制通道。
     */
    private static void connectControlSocket(String deviceId) throws IOException {
        Logger.debug("control: connecting to port " + ConstService.SCRCPY_PORT);
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress("127.0.0.1", ConstService.SCRCPY_PORT), 2000);
        sock.setTcpNoDelay(true);
        sock.setSoTimeout(3000); // 设置超时，避免无限等待
        Logger.debug("control: connected to socket");
        
        // 尝试读取 1 个 dummy byte（服务器可能会发送，也可能不发送）
        try {
            int b = sock.getInputStream().read();
            Logger.debug("control: read dummy byte result=" + b);
        } catch (IOException e) {
            Logger.debug("control: no dummy byte to read - " + e.getMessage());
        }

        // 初始化控制服务
        ControlService.startControl(deviceId);

        Logger.info("control: socket connected");
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
                // 触发断开回调，通知上层清理界面
                notifyDisconnect("video stream closed");
            }
        }, "scrcpy-decode");
        decodeThread.set(t);
        t.start();
    }

    /**
     *   @desc : 通知断开监听器
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param reason : 断开原因
     */
    private static void notifyDisconnect(String reason) {
        // 恢复默认窗口标题
        MainFrame.updateTitle(null);
        
        OnDisconnectListener listener = disconnectListener;
        if (listener != null) {
            try {
                listener.onDisconnect(reason);
            } catch (Exception e) {
                Logger.error("disconnect listener error: " + e);
            }
        }
    }

    /**
     *   @desc : 解码循环
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    private static void decodeLoop() throws Exception {
        // 等待手机上的 scrcpy-server 启动完成
        Thread.sleep(200);

        final int maxAttempts = 50;   // 最多重试 50 次
        final int retryGapMs = 100;   // 每次重试间隔 100ms（更快检测连接）
        Socket videoSocket = null;
        Socket controlSocketLocal = null;
        ScrcpyVideoPacketReader reader = null;
        IOException lastFail = null;
        boolean headerOk = false;

        // 步骤 1：建立视频和控制两个 socket 连接
        // 关键：必须先建立两个连接，服务器才知道视频和控制分别用哪个 socket
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // 清理之前的连接
            if (reader != null) {
                try { reader.close(); } catch (Exception ignore) {}
                reader = null;
            }
            if (videoSocket != null) {
                try { videoSocket.close(); } catch (IOException ignore) {}
                videoSocket = null;
            }
            if (controlSocketLocal != null) {
                try { controlSocketLocal.close(); } catch (IOException ignore) {}
                controlSocketLocal = null;
            }

            try {
                Logger.debug("scrcpy connect attempt " + attempt + "/" + maxAttempts);

                // 1.1 建立视频连接
                videoSocket = new Socket();
                videoSocket.connect(new InetSocketAddress("127.0.0.1", ConstService.SCRCPY_PORT), 2000);
                videoSocket.setTcpNoDelay(true);

                // 1.2 建立控制连接
                controlSocketLocal = new Socket();
                controlSocketLocal.connect(new InetSocketAddress("127.0.0.1", ConstService.SCRCPY_PORT), 2000);
                controlSocketLocal.setTcpNoDelay(true);
                controlSocketLocal.setSoTimeout(3000);

                // 1.3 读取视频头
                reader = new ScrcpyVideoPacketReader(videoSocket.getInputStream());
                reader.readHeader();
                headerOk = true;
                break;
            } catch (IOException e) {
                lastFail = e;
                Logger.debug("scrcpy: waiting for device (" + e.getMessage() + ")");
                Thread.sleep(retryGapMs);
            }
        }

        // 记录 socket 和 reader 引用，供 shutdown() 使用
        currentSocket.set(videoSocket);
        ScrcpyService.controlSocket.set(controlSocketLocal);
        currentReader.set(reader);

        // 连接失败，抛出异常
        if (!headerOk) {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignore) {}
            }
            if (videoSocket != null) {
                try { videoSocket.close(); } catch (IOException ignore) {}
            }
            if (controlSocketLocal != null) {
                try { controlSocketLocal.close(); } catch (IOException ignore) {}
            }
            throw new IOException("scrcpy: failed to read video header after " + maxAttempts + " attempts", lastFail);
        }

        // 步骤 2：获取视频分辨率
        final int vw = reader.getWidth();
        final int vh = reader.getHeight();
        Logger.info("scrcpy: video size " + vw + "x" + vh);

        // 更新控制服务的视频尺寸
        ControlService.updateVideoSize(vw, vh);

        // 步骤 2.5：初始化控制服务
        // 控制 socket 已经建立，现在初始化控制服务
        String deviceId = activeDeviceId;
        if (deviceId != null) {
            ControlService.startControlWithSocket(controlSocketLocal, deviceId);
            
            // 连接成功后显示浮动工具窗口
            SwingUtilities.invokeLater(() -> {
                com.tyf.demo.gui.ToolWindow.showToolWindow(com.tyf.demo.gui.MainFrame.getMainFrame());
            });
        }

        // 步骤 3：视频解码主循环
        // 从 socket 读取 H.264 数据包 → 使用 FFmpeg 解码为原始图像 → 绘制到 UI
        try (Socket sock = videoSocket) {
            try (ScrcpyVideoPacketReader r = reader) {
                AtomicLong decodedFrames = new AtomicLong();
                ScrcpyFrameSink sink = (packed, w, h) -> {
                    long n = decodedFrames.incrementAndGet();
                    int len = packed == null ? 0 : packed.length;
                    int expect = w * h * 3;  // BGR 格式，每像素 3 字节

                    // 抽样日志：前 3 帧 + 每隔 300 帧（约每 5 秒）
                    // if (n <= 3 || n % 300 == 0) {
                    //     Logger.debug("scrcpy: frame#" + n + " " + w + "x" + h + " bytes=" + len);
                    // }
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
                            boolean sample = (accessUnits <= 3 || accessUnits % 300 == 0);
                            // if (sample) {
                            //     Logger.debug("scrcpy: au#" + accessUnits + " bytes=" + (au == null ? 0 : au.length));
                            // }

                            // 计时：记录解码耗时
                            long t0 = sample ? System.nanoTime() : 0;

                            // 解码 H.264 数据包
                            dec.decode(au, sink);

                            // if (sample) {
                            //     long dtMs = (System.nanoTime() - t0) / 1_000_000L;
                            //     Logger.debug("scrcpy: au#" + accessUnits + " time=" + dtMs + "ms");
                            // }
                        } catch (IOException e) {
                            Logger.debug("scrcpy: stream closed/EOF");
                            break;
                        } catch (Throwable t) {
                            Logger.error("scrcpy: decode loop error - " + t);
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
            Logger.info("scrcpy server jar exists: " + out.getAbsolutePath());
            return out;
        }

        // 从 resources 读取并写入本地文件
        try (InputStream in = ScrcpyService.class.getResourceAsStream(ConstService.SCRCPY_SERVER_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource file: " + ConstService.SCRCPY_SERVER_RESOURCE);
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
        }
        Logger.info("scrcpy server jar extracted: " + out.getAbsolutePath());
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
