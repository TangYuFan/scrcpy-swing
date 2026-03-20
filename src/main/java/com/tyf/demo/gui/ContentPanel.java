package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

/**
 * @desc : 中间内容区（显示手机画面）
 * @auth : tyf
 * @date : 2026-03-18
 */
public class ContentPanel extends JPanel {

    private volatile BufferedImage frame;

    public ContentPanel() {

        setLayout(new BorderLayout());
        setBackground(ConstService.COLOR_BLACK);
        setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));

    }

    /**
     * 在解码线程调用：仅投递 BGR 像素到 EDT 再构造 {@link BufferedImage}，避免非 EDT 持有图像 + D3D 缩放第二帧崩溃。
     */
    public void postFramePackedBgr(byte[] packedBgr, int w, int h) {
        if (packedBgr == null || w <= 0 || h <= 0) {
            return;
        }
        int need = w * h * 3;
        if (packedBgr.length < need) {
            return;
        }
        final byte[] copy = Arrays.copyOf(packedBgr, need);
        SwingUtilities.invokeLater(() -> {
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            byte[] dst = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
            System.arraycopy(copy, 0, dst, 0, need);
            this.frame = bi;
            repaint();
        });
    }

    /** @deprecated 优先用 {@link #postFramePackedBgr(byte[], int, int)} */
    @Deprecated
    public void setFrame(BufferedImage img) {
        this.frame = img;
        SwingUtilities.invokeLater(this::repaint);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage img = frame;
        if (img == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int pw = getWidth();
            int ph = getHeight();
            int iw = img.getWidth();
            int ih = img.getHeight();
            if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) {
                return;
            }
            double scale = Math.min((double) pw / iw, (double) ph / ih);
            int dw = (int) Math.round(iw * scale);
            int dh = (int) Math.round(ih * scale);
            int dx = (pw - dw) / 2;
            int dy = (ph - dh) / 2;
            g2.drawImage(img, dx, dy, dw, dh, null);
        } finally {
            g2.dispose();
        }
    }

}