package com.tyf.demo.service;

/**
 *   @desc : Scrcpy 帧回调接口
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
@FunctionalInterface
public interface ScrcpyFrameSink {
    void onPackedBgr(byte[] packedBgr, int w, int h);
}
