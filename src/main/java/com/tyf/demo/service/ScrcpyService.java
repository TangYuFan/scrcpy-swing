package com.tyf.demo.service;

import com.tyf.demo.entity.Device;
import com.tyf.demo.gui.MainPanel;
import com.tyf.demo.util.CmdTools;
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
 * scrcpy-server runner + video receiver（PC 端纯 Java）。
 * <p>
 * <b>与 QtScrcpy / 官方 scrcpy 的差异（为何本方案更易卡、延迟大）：</b>
 * <ul>
 *   <li>QtScrcpy：进程内链接 libavcodec，Demuxer 独立线程读 socket，解码后 YUV 走 OpenGL，无「每帧 8MB BGR 管道」。</li>
 *   <li>官方 scrcpy：Rust/SDL，同样进程内解码 + 纹理，延迟极低。</li>
 *   <li>本工程：使用 JavaCPP（JavaCV 底层）进程内 FFmpeg 解码，最终仍需要把帧转换为 BGR 并交给 Swing 绘制。</li>
 * </ul>
 * 缓解手段：设备端 {@link #SCRCPY_MAX_SIZE} 降分辨率、必要时丢帧/降帧率、以及优化 Swing 侧绘制与拷贝。
 */
public final class ScrcpyService {

    private static final String SERVER_RESOURCE = "/scrcpy/scrcpy-server-v3.3.4.jar";
    private static final String DEVICE_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar";
    private static final int VIDEO_FORWARD_PORT = 27183; // any free local port

    /**
     * 设备编码最大边（scrcpy {@code max_size}）。0=原生分辨率（PC 软解压力最大）。
     * 1280 可在多数机上明显改善流畅度；要清晰度可调大或设 0。
     */
    private static final int SCRCPY_MAX_SIZE = 1280;

    /**
     * 为 true 时将解码后的帧绘制到 {@link com.tyf.demo.gui.ContentPanel}；
     * 为 false 时仅打抽样日志（排障用）。
     */
    private static final boolean SCRCPY_DRAW_DECODED_TO_UI = true;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean decoderRunning = new AtomicBoolean(false);

    /** 长时间运行的「adb shell … app_process」子进程，必须在退出时 destroy，否则会占用 adb.exe。 */
    private static final AtomicReference<Process> scrcpyAdbShell = new AtomicReference<>();

    private static volatile String activeDeviceId;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ScrcpyService::shutdown, "scrcpy-adb-cleanup"));
    }

    private ScrcpyService() {}

    public static File initLocalServerJar() throws Exception {
        return extractServerJar(true);
    }

    public static void start(Device device) throws Exception {
        if (!running.compareAndSet(false, true)) {
            Logger.info("scrcpy already running");
            return;
        }

        boolean started = false;
        String deviceId = device.getName();
        try {
            activeDeviceId = deviceId;

            // 1) Ensure local server jar exists
            File localJar = extractServerJar(false);

            // 2) Push jar
            adb(deviceId, "push \"" + localJar.getAbsolutePath() + "\" " + DEVICE_SERVER_PATH);

            // 3) Forward local port to localabstract "scrcpy"
            // tunnel_forward=true => server creates LocalServerSocket("scrcpy") and accepts (video only if audio/control disabled)
            // remove stale forward to avoid half-open / wrong peer (ignore errors)
            adbQuiet(deviceId, "forward --remove tcp:" + VIDEO_FORWARD_PORT);
            adb(deviceId, "forward tcp:" + VIDEO_FORWARD_PORT + " localabstract:scrcpy");

            // 4) Start server (async), rely on stderr/stdout for logs
            // Keep it simple: video only, no control, no audio, no metas for now
            String startCmd =
                    "shell CLASSPATH=" + DEVICE_SERVER_PATH + " app_process / com.genymobile.scrcpy.Server 3.3.4 " +
                            "log_level=info " +
                            "video=true audio=false control=false " +
                            "tunnel_forward=true " +
                            // align with QtScrcpyCore/scrcpy packet mode
                            "raw_stream=false " +
                            "send_device_meta=true send_codec_meta=true send_frame_meta=true send_dummy_byte=true " +
                            "max_fps=60" +
                            (SCRCPY_MAX_SIZE > 0 ? " max_size=" + SCRCPY_MAX_SIZE : "");
            String fullStartCmd = adbCmd(deviceId) + " " + startCmd;
            Logger.info(fullStartCmd);
            Process shell = CmdTools.startBackgroundProcess(fullStartCmd);
            scrcpyAdbShell.set(shell);

            // 5) Connect and decode (async)
            startDecodeThread();
            started = true;
        } catch (Exception e) {
            adbQuiet(deviceId, "forward --remove tcp:" + VIDEO_FORWARD_PORT);
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
     * 关闭投屏相关 adb 子进程与端口转发，释放本程序对 adb.exe 的占用。
     * 在窗口关闭、shutdown hook 中调用；可重复调用。
     */
    public static void shutdown() {
        Process p = scrcpyAdbShell.getAndSet(null);
        destroyQuietly(p);

        String deviceId = activeDeviceId;
        activeDeviceId = null;
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            adbQuiet(deviceId, "forward --remove tcp:" + VIDEO_FORWARD_PORT);
        }

        // 结束由本目录 adb 启动的 adb server，进一步释放 adb.exe / dll 的文件映射（若与其它 IDE 共用系统 adb 则可能互相影响）
        try {
            CmdTools.exec(adbCmd(null) + " kill-server");
        } catch (Exception ignore) {
            // exec 内部已捕获；兜底
        }

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

    private static void startDecodeThread() {
        if (!decoderRunning.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            try {
                decodeLoop();
            } catch (Throwable t) {
                Logger.error("scrcpy decode thread stopped: " + t);
                t.printStackTrace();
            } finally {
                decoderRunning.set(false);
                running.set(false);
            }
        }, "scrcpy-decode").start();
    }

    private static void decodeLoop() throws Exception {
        // tunnel_forward: device listens then accept(). PC often connects before accept → immediate EOF.
        // Same idea as QtScrcpyCore: retry connect + read first bytes until header is readable.
        Thread.sleep(400);
        final int maxAttempts = 50;
        final int retryGapMs = 200;
        Socket s = null;
        ScrcpyVideoPacketReader reader = null;
        IOException lastFail = null;
        boolean headerOk = false;
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
                Logger.info("scrcpy video connect attempt " + attempt + "/" + maxAttempts + " -> 127.0.0.1:" + VIDEO_FORWARD_PORT);
                s = new Socket();
                s.connect(new InetSocketAddress("127.0.0.1", VIDEO_FORWARD_PORT), 5000);
                s.setTcpNoDelay(true);
                s.setSoTimeout(0);
                reader = new ScrcpyVideoPacketReader(s.getInputStream());
                reader.readHeader();
                headerOk = true;
                break;
            } catch (IOException e) {
                lastFail = e;
                Logger.info("scrcpy: waiting for device accept / header (" + e.getMessage() + ")");
                Thread.sleep(retryGapMs);
            }
        }
        if (!headerOk) {
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
            throw new IOException("scrcpy: failed to read stream header after " + maxAttempts + " attempts", lastFail);
        }

        final int vw = reader.getWidth();
        final int vh = reader.getHeight();
        Logger.info("scrcpy: 视频解码 " + vw + "x" + vh + "（JavaCV/FFmpeg 进程内解码）");

        try (Socket sock = s) {
            try (ScrcpyVideoPacketReader r = reader) {
                AtomicLong decodedFrames = new AtomicLong();
                ScrcpyFrameSink sink = (packed, w, h) -> {
                    long n = decodedFrames.incrementAndGet();
                    int len = packed == null ? 0 : packed.length;
                    int expect = w * h * 3;
                    // 抽样日志：前几帧 + 每隔若干帧（避免刷屏）
                    if (n <= 5 || n % 120 == 0) {
                        int b0 = (packed != null && len > 0) ? (packed[0] & 0xff) : -1;
                        int bLast = (packed != null && len > 1) ? (packed[len - 1] & 0xff) : -1;
                        Logger.info("scrcpy: decode OK frame#" + n + " " + w + "x" + h
                                + " bytes=" + len + (len == expect ? "" : " (expect " + expect + ")")
                                + " b[0]=" + b0 + " b[last]=" + bLast);
                    }
                    if (SCRCPY_DRAW_DECODED_TO_UI && MainPanel.getContentPanel() != null) {
                        MainPanel.getContentPanel().postFramePackedBgr(packed, w, h);
                    }
                };

                try (ScrcpyH264Decoder dec = new ScrcpyH264Decoder(vw, vh)) {
                    long accessUnits = 0;
                    while (true) {
                        try {
                            byte[] au = r.nextMediaPacket();
                            accessUnits++;
                            boolean sample = (accessUnits <= 5 || accessUnits % 120 == 0);
                            if (sample) {
                                Logger.info("scrcpy: access-units=" + accessUnits + " auBytes=" + (au == null ? 0 : au.length));
                            }
                            long t0 = sample ? System.nanoTime() : 0;
                            dec.decode(au, sink);
                            if (sample) {
                                long dtMs = (System.nanoTime() - t0) / 1_000_000L;
                                Logger.info("scrcpy: decode AU#" + accessUnits + " dt=" + dtMs + "ms");
                            }
                        } catch (IOException e) {
                            Logger.info("scrcpy: stream closed or read error — " + e.getMessage());
                            break;
                        } catch (Throwable t) {
                            Logger.error("scrcpy: decode loop error — " + t);
                            t.printStackTrace();
                            break;
                        }
                    }
                }
            }
        }
    }

    private static File extractServerJar(boolean forceRewrite) throws Exception {
        File dir = new File(ConstService.WORKSPACE, "scrcpy");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File out = new File(dir, "scrcpy-server-v3.3.4.jar");
        if (out.exists() && !forceRewrite) {
            Logger.info("scrcpy server jar: " + out.getAbsolutePath());
            return out;
        }

        try (InputStream in = ScrcpyService.class.getResourceAsStream(SERVER_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + SERVER_RESOURCE);
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
        }
        Logger.info("scrcpy server jar: " + out.getAbsolutePath());
        return out;
    }

    private static void adb(String deviceId, String args) {
        String cmd = adbCmd(deviceId) + " " + args;
        Logger.info(cmd);
        String rt = CmdTools.exec(cmd);
        if (rt != null && !rt.trim().isEmpty()) {
            Logger.info(rt);
        }
    }

    /** adb command that may legitimately fail (e.g. forward --remove when none). */
    private static void adbQuiet(String deviceId, String args) {
        String cmd = adbCmd(deviceId) + " " + args;
        CmdTools.exec(cmd);
    }

    private static String adbCmd(String deviceId) {
        String adb = ConstService.ADB_PATH + "adb.exe";
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return adb;
        }
        return adb + " -s " + deviceId;
    }
}

