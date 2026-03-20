package com.tyf.demo.service;

import org.pmw.tinylog.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

/**
 *   @desc : Scrcpy 视频包读取器
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
final class ScrcpyVideoPacketReader implements AutoCloseable {

    private static final int DEVICE_NAME_LEN = 64;
    private static final int HEADER_LEN = 12;
    private static final long FLAG_CONFIG = (1L << 63);
    private static final int CODEC_H264 = 0x68323634;
    private static final int CODEC_H265 = 0x68323635;
    private static final int CODEC_AV1 = 0x00617631;

    private final PushbackInputStream in;
    private int width;
    private int height;
    private boolean headerRead;
    private long packetCount;
    private byte[] pendingConfig;
    /** 最近一次收集到的 SPS/PPS 配置缓存，用于后续 AU 继续解码 */
    private byte[] lastConfig;

    ScrcpyVideoPacketReader(InputStream raw) {
        this.in = new PushbackInputStream(new BufferedInputStream(raw), 1);
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    void readHeader() throws IOException {
        if (headerRead) {
            return;
        }
        headerRead = true;

        int first = in.read();
        if (first < 0) {
            throw new IOException("scrcpy demux: EOF before stream header");
        }
        if (first == 0) {
            Logger.info("scrcpy demux: consumed dummy byte");
        } else {
            in.unread(first);
        }

        byte[] deviceNameRaw = readExactly(DEVICE_NAME_LEN);
        int end = 0;
        while (end < deviceNameRaw.length && deviceNameRaw[end] != 0) {
            end++;
        }
        String name = new String(deviceNameRaw, 0, end, StandardCharsets.UTF_8).trim();
        if (!name.isEmpty()) {
            Logger.info("scrcpy demux: device meta name=" + name);
        } else {
            Logger.info("scrcpy demux: device meta detected");
        }

        int codec = be32(readExactly(4), 0);
        if (codec != CODEC_H264 && codec != CODEC_H265 && codec != CODEC_AV1) {
            throw new IOException("scrcpy demux: invalid codec id: 0x" + Integer.toHexString(codec));
        }
        if (codec != CODEC_H264) {
            throw new IOException("scrcpy demux: only H264 supported in Java decoder");
        }
        Logger.info("scrcpy demux: codec=h264");
        byte[] size = readExactly(8);
        width = be32(size, 0);
        height = be32(size, 4);
        Logger.info("scrcpy demux: video size=" + width + "x" + height);
    }

    /**
     *   @desc : 读取下一个媒体数据包
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    byte[] nextMediaPacket() throws IOException {
        while (true) {
            byte[] header = readExactly(HEADER_LEN);
            long ptsFlags = be64(header, 0);
            int len = be32(header, 8);
            if (len <= 0 || len > (16 * 1024 * 1024)) {
                throw new IOException("scrcpy demux: invalid packet size=" + len);
            }
            byte[] payload = readExactly(len);
            boolean config = (ptsFlags & FLAG_CONFIG) != 0;
            packetCount++;
            if (packetCount <= 5 || packetCount % 120 == 0) {
                Logger.info("scrcpy demux: packet#" + packetCount + " bytes=" + len + " config=" + config);
            }

            if (config) {
                if (pendingConfig == null || pendingConfig.length == 0) {
                    pendingConfig = payload;
                } else {
                    byte[] mergedCfg = new byte[pendingConfig.length + payload.length];
                    System.arraycopy(pendingConfig, 0, mergedCfg, 0, pendingConfig.length);
                    System.arraycopy(payload, 0, mergedCfg, pendingConfig.length, payload.length);
                    pendingConfig = mergedCfg;
                }
                continue;
            }

            // 把 SPS/PPS（config）前置到 media payload，避免解码器对“只有 slice、没有上下文”崩溃。
            if (pendingConfig != null && pendingConfig.length > 0) {
                byte[] merged = new byte[pendingConfig.length + payload.length];
                System.arraycopy(pendingConfig, 0, merged, 0, pendingConfig.length);
                System.arraycopy(payload, 0, merged, pendingConfig.length, payload.length);
                payload = merged;
                lastConfig = pendingConfig; // 缓存起来，后续 AU 也带上
                pendingConfig = null;
            } else if (lastConfig != null && lastConfig.length > 0) {
                // 避免重复前置：如果当前 media payload 已经包含 SPS/PPS（设备可能周期性发送 config），
                // 再把 lastConfig 拼到前面可能会让访问单元边界变得异常。
                if (!containsSpsPps(payload)) {
                    byte[] merged = new byte[lastConfig.length + payload.length];
                    System.arraycopy(lastConfig, 0, merged, 0, lastConfig.length);
                    System.arraycopy(payload, 0, merged, lastConfig.length, payload.length);
                    payload = merged;
                }
            }
            return payload;
        }
    }

    private static boolean containsSpsPps(byte[] b) {
        if (b == null || b.length < 8) return false;
        // 扫描前段即可：H264 start code 后紧跟 nal header，type=(nalHeader & 0x1F)
        int limit = Math.min(b.length - 4, 8192);
        for (int i = 0; i <= limit; i++) {
            // 00 00 00 01
            if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 0 && b[i + 3] == 1 && i + 4 < b.length) {
                int nalHeader = b[i + 4] & 0xFF;
                int type = nalHeader & 0x1F;
                if (type == 7 || type == 8) return true;
            }
            // 00 00 01
            if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1 && i + 3 < b.length) {
                int nalHeader = b[i + 3] & 0xFF;
                int type = nalHeader & 0x1F;
                if (type == 7 || type == 8) return true;
            }
        }
        return false;
    }

    private byte[] readExactly(int n) throws IOException {
        byte[] out = new byte[n];
        int pos = 0;
        while (pos < n) {
            int r = in.read(out, pos, n - pos);
            if (r < 0) {
                throw new IOException("scrcpy demux: unexpected EOF");
            }
            pos += r;
        }
        return out;
    }

    private static int be32(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24)
                | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8)
                | (b[off + 3] & 0xFF);
    }

    private static long be64(byte[] b, int off) {
        return ((long) (b[off] & 0xFF) << 56)
                | ((long) (b[off + 1] & 0xFF) << 48)
                | ((long) (b[off + 2] & 0xFF) << 40)
                | ((long) (b[off + 3] & 0xFF) << 32)
                | ((long) (b[off + 4] & 0xFF) << 24)
                | ((long) (b[off + 5] & 0xFF) << 16)
                | ((long) (b[off + 6] & 0xFF) << 8)
                | ((long) (b[off + 7] & 0xFF));
    }

    /**
     *   @desc : 关闭资源
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException ignore) {}
    }
}
