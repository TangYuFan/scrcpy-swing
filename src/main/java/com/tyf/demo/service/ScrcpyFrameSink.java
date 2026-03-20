package com.tyf.demo.service;

/**
 * 一帧 BGR 像素（紧密排列，长度 w*h*3），供 JavaCV 或 FFmpeg 管道解码后回传 UI。
 */
@FunctionalInterface
public interface ScrcpyFrameSink {
    void onPackedBgr(byte[] packedBgr, int w, int h);
}
