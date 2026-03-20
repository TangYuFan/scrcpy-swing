package com.tyf.demo.service;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.Loader;
import org.pmw.tinylog.Logger;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;

/**
 *   @desc : H.264 解码器
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
final class ScrcpyH264Decoder implements AutoCloseable {

    private static final Object NATIVE_INIT = new Object();
    private static volatile boolean nativeReady;

    private AVCodecContext codecCtx;
    private AVFrame frame;
    private AVPacket packet;
    private SwsContext sws;
    private BytePointer rgbBuf;
    private IntPointer rgbLinesizes;
    private PointerPointer<BytePointer> rgbPointers;
    private int lastRgbBufSize;
    /** 复用输入 packet 缓冲，避免 av_new_packet + put 触发 native 堆不稳定 */
    private BytePointer pktBuf;
    private AVFrame decodingFrame;
    private AVFrame renderingFrame;
    private int lastSwsW;
    private int lastSwsH;
    private int lastSwsFmt = Integer.MIN_VALUE;

    private long auInCount = 0;

    private static void ensureFfmpegNative() {
        if (nativeReady) {
            return;
        }
        synchronized (NATIVE_INIT) {
            if (nativeReady) {
                return;
            }
            Loader.load(org.bytedeco.ffmpeg.global.avcodec.class);
            Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
            Loader.load(org.bytedeco.ffmpeg.global.swscale.class);
            nativeReady = true;
        }
    }

    ScrcpyH264Decoder(int width, int height) {
        ensureFfmpegNative();

        AVCodec codec = avcodec_find_decoder(AV_CODEC_ID_H264);
        if (codec == null) {
            throw new IllegalStateException("H264 decoder not found");
        }
        codecCtx = avcodec_alloc_context3(codec);
        if (codecCtx == null) {
            throw new IllegalStateException("avcodec_alloc_context3 failed");
        }
        codecCtx.width(width);
        codecCtx.height(height);
        // 解码端像素格式由码流决定；强行 YUV420P + LOW_DELAY 部分 H.264 会在后续帧触发 native 崩溃
        codecCtx.pix_fmt(AV_PIX_FMT_NONE);
        // Windows + JavaCPP 下 H.264 帧级多线程解码易在后续帧 native 崩溃，强制单线程
        codecCtx.thread_count(1);

        int openRet = avcodec_open2(codecCtx, codec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
        if (openRet < 0) {
            freeAll();
            throw new IllegalStateException("avcodec_open2 failed: " + openRet);
        }

        frame = av_frame_alloc();
        packet = av_packet_alloc();
        if (frame == null || packet == null) {
            freeAll();
            throw new IllegalStateException("av_frame_alloc / av_packet_alloc failed");
        }
        decodingFrame = av_frame_alloc();
        renderingFrame = av_frame_alloc();
        if (decodingFrame == null || renderingFrame == null) {
            freeAll();
            throw new IllegalStateException("dual frame alloc failed");
        }
    }

    void decode(byte[] annexB, ScrcpyFrameSink out) {
        if (annexB == null || annexB.length == 0) {
            return;
        }
        try {
            long idx = ++auInCount;
            byte[] data = annexB;
            boolean annexBLike = containsAnnexBStartCode(annexB);
            if (!annexBLike) {
                // 不能只看“开头是不是 00 00 00 01”，有些 packet 的 payload 开头可能不是 start code，
                // 但内部仍包含 start code（我们扫描前段/内部特征做更稳的判断）。
                boolean avccLike = looksLikeAvccLengthPrefixed(annexB);
                if (!avccLike) {
                    if (idx <= 3) {
                        Logger.error("scrcpy decode: skip unknown AU format bytes=" + annexB.length
                                + " head=[" + headHex(annexB) + "]");
                    }
                    return;
                }
                data = avccToAnnexBValidated(annexB);
                if (data == null || !containsAnnexBStartCode(data)) {
                    if (idx <= 3) {
                        Logger.error("scrcpy decode: AVCC->AnnexB invalid bytes in=" + annexB.length
                                + " out=" + (data == null ? -1 : data.length)
                                + " inHead=[" + headHex(annexB) + "] outHead=[" + headHex(data) + "]");
                    }
                    return;
                }
            }

            if (idx <= 3) {
                Logger.info("scrcpy decode: AU#" + idx + " inBytes=" + annexB.length
                        + " useAnnexB=" + containsAnnexBStartCode(data)
                        + " inHead=[" + headHex(annexB) + "]");
            }

            // 采样：仅对较小前几包在外层调用处已做日志，这里避免刷屏
            // 这里的日志主要用于定位 native 崩溃点（最后一条日志就是崩溃前的位置）
            // Logger.info("scrcpy decode: AU bytes=" + data.length + " head=[" + headHex(data) + "]");

            av_packet_unref(packet);
            if (pktBuf == null || pktBuf.capacity() < data.length) {
                if (pktBuf != null) {
                    pktBuf.close();
                }
                pktBuf = new BytePointer(data.length);
            }
            pktBuf.position(0);
            pktBuf.put(data, 0, data.length);
            pktBuf.position(0);
            packet.data(pktBuf);
            packet.size(data.length);
            packet.pts(AV_NOPTS_VALUE);
            packet.dts(AV_NOPTS_VALUE);

            if (idx <= 3) {
                Logger.info("scrcpy decode: AU#" + idx + " send_packet dataLen=" + data.length);
            }
            int send = avcodec_send_packet(codecCtx, packet);
            if (send < 0) {
                Logger.error("scrcpy avcodec_send_packet: " + send);
                return;
            }
            if (idx <= 3) {
                Logger.info("scrcpy decode: AU#" + idx + " send_packet ok ret=" + send);
            }

            while (true) {
                av_frame_unref(decodingFrame);
                int rec = avcodec_receive_frame(codecCtx, decodingFrame);
                if (rec == AVERROR_EAGAIN() || rec == AVERROR_EOF()) {
                    break;
                }
                if (rec < 0) {
                    Logger.error("scrcpy avcodec_receive_frame: " + rec);
                    break;
                }
                if (idx <= 3) {
                    Logger.info("scrcpy decode: AU#" + idx + " receive_frame ok fmt=" + decodingFrame.format()
                            + " w=" + decodingFrame.width() + " h=" + decodingFrame.height());
                }

                int fw = decodingFrame.width();
                int fh = decodingFrame.height();
                int srcFmt = decodingFrame.format();
                if (fw <= 0 || fh <= 0) {
                    Logger.error("scrcpy: invalid frame size " + fw + "x" + fh);
                    continue;
                }
                if (srcFmt == AV_PIX_FMT_NONE || srcFmt < 0) {
                    Logger.error("scrcpy: invalid src pixel fmt=" + srcFmt + " for frame " + fw + "x" + fh);
                    continue;
                }

                boolean swsChanged = sws != null && (fw != lastSwsW || fh != lastSwsH || srcFmt != lastSwsFmt);
                if (swsChanged) {
                    sws_freeContext(sws);
                    sws = null;
                    Logger.info("scrcpy: video size changed, rebuilding SWS: " + lastSwsW + "x" + lastSwsH + " -> " + fw + "x" + fh);
                }
                lastSwsW = fw;
                lastSwsH = fh;
                lastSwsFmt = srcFmt;

                sws = sws_getCachedContext(
                        sws,
                        fw, fh, srcFmt,
                        fw, fh, AV_PIX_FMT_BGR24,
                        SWS_BILINEAR, null, null, (double[]) null);
                if (sws == null) {
                    Logger.error("scrcpy sws_getCachedContext failed fmt=" + srcFmt);
                    continue;
                }

                int rgbSize = av_image_get_buffer_size(AV_PIX_FMT_BGR24, fw, fh, 1);
                boolean rgbBufTooSmall = rgbBuf == null || rgbBuf.capacity() < rgbSize;
                if (rgbBufTooSmall || swsChanged) {
                    if (rgbBuf != null) {
                        rgbBuf.close();
                    }
                    rgbBuf = new BytePointer(rgbSize);
                    lastRgbBufSize = rgbSize;
                    rgbLinesizes = new IntPointer(4);
                    rgbPointers = new PointerPointer<>(4);
                    av_image_fill_arrays(rgbPointers, rgbLinesizes, rgbBuf, AV_PIX_FMT_BGR24, fw, fh, 1);
                    Logger.info("scrcpy: rebuilt RGB buffer for " + fw + "x" + fh);
                }

                if (idx <= 3) {
                    Logger.info("scrcpy decode: AU#" + idx + " before sws_scale");
                }
                int scaled = sws_scale(
                        sws,
                        decodingFrame.data(), decodingFrame.linesize(),
                        0, fh,
                        rgbPointers, rgbLinesizes);
                if (scaled < 0) {
                    Logger.error("scrcpy sws_scale: " + scaled);
                    continue;
                }
                if (idx <= 3) {
                    Logger.info("scrcpy decode: AU#" + idx + " after sws_scale ret=" + scaled);
                }

                byte[] packed = copyBgrToPacked(rgbBuf, rgbLinesizes.get(0), fw, fh);
                if (packed != null) {
                    try {
                        out.onPackedBgr(packed, fw, fh);
                    } catch (Throwable cb) {
                        Logger.error("scrcpy onPackedBgr failed: " + cb);
                        cb.printStackTrace();
                    }
                }
            }
        } catch (Throwable t) {
            Logger.error("scrcpy decode packet failed: " + t);
            t.printStackTrace();
        }
    }

    private static byte[] copyBgrToPacked(BytePointer src, int stride, int w, int h) {
        int need = w * h * 3;
        byte[] dst = new byte[need];
        int rowBytes = Math.min(stride, w * 3);
        for (int y = 0; y < h; y++) {
            src.position((long) y * stride).get(dst, y * w * 3, rowBytes);
        }
        return dst;
    }

    private void freeAll() {
        if (sws != null) {
            sws_freeContext(sws);
            sws = null;
        }
        if (pktBuf != null) {
            pktBuf.close();
            pktBuf = null;
        }
        if (rgbPointers != null) {
            rgbPointers.close();
            rgbPointers = null;
        }
        if (rgbLinesizes != null) {
            rgbLinesizes.close();
            rgbLinesizes = null;
        }
        if (rgbBuf != null) {
            rgbBuf.close();
            rgbBuf = null;
        }
        if (decodingFrame != null) {
            av_frame_free(decodingFrame);
            decodingFrame = null;
        }
        if (renderingFrame != null) {
            av_frame_free(renderingFrame);
            renderingFrame = null;
        }
        if (frame != null) {
            av_frame_free(frame);
            frame = null;
        }
        if (packet != null) {
            av_packet_free(packet);
            packet = null;
        }
        if (codecCtx != null) {
            avcodec_free_context(codecCtx);
            codecCtx = null;
        }
    }

    private static boolean startsWithAnnexB(byte[] b) {
        if (b.length >= 4 && b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 1) {
            return true;
        }
        return b.length >= 3 && b[0] == 0 && b[1] == 0 && b[2] == 1;
    }

    private static boolean containsAnnexBStartCode(byte[] b) {
        if (b == null || b.length < 4) {
            return false;
        }
        // 只扫描前段，足够判断“是否为 AnnexB”特征，避免 O(n) 花太多时间
        int limit = Math.min(b.length - 4, 2048);
        for (int i = 0; i <= limit; i++) {
            // 00 00 00 01
            if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 0 && b[i + 3] == 1) {
                return true;
            }
            // 00 00 01
            if (i + 2 < b.length && b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeAvccLengthPrefixed(byte[] b) {
        if (b == null || b.length < 8) {
            return false;
        }
        int pos = 0;
        int guard = 0;
        int nalCount = 0;
        while (pos + 4 <= b.length) {
            guard++;
            if (guard > 2048) {
                return false;
            }
            int n = ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16)
                    | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            pos += 4;
            if (n <= 0 || n > (16 * 1024 * 1024)) {
                return false;
            }
            if (pos + n > b.length) {
                return false;
            }
            nalCount++;
            pos += n;
        }
        // 必须“恰好”吃完，避免把 AnnexB 当成 AVCC
        return nalCount > 0 && pos == b.length;
    }

    private static byte[] avccToAnnexBValidated(byte[] buf) {
        // 4字节 big-endian NAL length + NAL payload ...
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(buf.length + 64);
        int pos = 0;
        int guard = 0;
        while (pos + 4 <= buf.length) {
            guard++;
            if (guard > 2048) {
                return null;
            }
            int n = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                    | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
            pos += 4;
            if (n <= 0) {
                return null;
            }
            // 必须保证 n 不会越界，且不能吞掉整个尾巴（否则可能是 AVCC/AnnexB 混淆）
            if (pos + n > buf.length) {
                return null;
            }
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(1);
            out.write(buf, pos, n);
            pos += n;
        }
        // AVCC 转换应该“恰好吃完”
        if (pos != buf.length) {
            return null;
        }
        return out.size() > 0 ? out.toByteArray() : null;
    }

    private static String headHex(byte[] b) {
        if (b == null || b.length == 0) {
            return "";
        }
        int n = Math.min(16, b.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", b[i]));
        }
        return sb.toString();
    }

    /**
     *   @desc : 关闭资源
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    @Override
    public void close() {
        freeAll();
    }

}
