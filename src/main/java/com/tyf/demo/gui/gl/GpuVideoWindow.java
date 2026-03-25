package com.tyf.demo.gui.gl;

import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @desc : 独立 GPU 渲染窗口（避免 Swing 内嵌重量级 Canvas 的混排问题）
 * @auth : tyf
 * @date : 2026-03-25
 */
public class GpuVideoWindow {

    private final JFrame frame;
    private final LwjglVideoCanvas canvas;

    public GpuVideoWindow(String title) {
        this.frame = new JFrame(title);
        this.canvas = new LwjglVideoCanvas();
        initUi();
    }

    private void initUi() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.setSize(960, 540);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Logger.info("[DEBUG] GpuVideoWindow closed");
            }
        });
    }

    public void showWindow() {
        if (SwingUtilities.isEventDispatchThread()) {
            frame.setVisible(true);
        } else {
            SwingUtilities.invokeLater(() -> frame.setVisible(true));
        }
    }

    public void closeWindow() {
        if (SwingUtilities.isEventDispatchThread()) {
            frame.dispose();
        } else {
            SwingUtilities.invokeLater(frame::dispose);
        }
    }

    public boolean isAlive() {
        return frame.isDisplayable();
    }

    public void submitFrame(byte[] packedBgr, int w, int h) {
        if (!isAlive()) {
            return;
        }
        canvas.submitFrame(packedBgr, w, h);
    }

    public void clearVideo() {
        canvas.clearVideo();
    }

    public boolean isRenderHealthy() {
        return canvas.isRenderHealthy();
    }
}

