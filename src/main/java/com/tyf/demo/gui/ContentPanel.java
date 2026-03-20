package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

/**
 *   @desc : 中间内容区（显示手机画面）
 *   @auth : tyf
 *   @date : 2026-03-20 14:04:14
*/
public class ContentPanel extends JPanel {

    /** 当前帧的图像缓存 */
    private volatile BufferedImage frame;
    /** 连接时的 loading 对话框引用（第一帧渲染后自动关闭） */
    private static JDialog loadingDialog;
    /** 当前视频分辨率 */
    private volatile int currentWidth;
    private volatile int currentHeight;
    /** 是否已初始化尺寸 */
    private volatile boolean sizeInitialized = false;

    public ContentPanel() {
        setLayout(new BorderLayout());
        setBackground(ConstService.COLOR_BLACK);
        // 初始尺寸，后续会动态调整
        setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
    }

    /**
     *   @desc : 重置尺寸状态
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void reset() {
        sizeInitialized = false;
        currentWidth = 0;
        currentHeight = 0;
        frame = null;
    }

    /**
     *   @desc : 关闭 loading 对话框（切换设备时使用）
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void closeLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog.dispose();
            loadingDialog = null;
        }
    }

    /**
     *   @desc : 获取当前视频宽度
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public int getCurrentWidth() {
        return currentWidth;
    }

    /**
     *   @desc : 获取当前视频高度
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public int getCurrentHeight() {
        return currentHeight;
    }

    /**
     *   @desc : 设置 loading 对话框
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static void setLoadingDialog(JDialog dialog) {
        loadingDialog = dialog;
    }

    /**
     *   @desc : 投递 BGR 帧到 UI，并处理尺寸变化
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public void postFramePackedBgr(byte[] packedBgr, int w, int h) {
        if (packedBgr == null || w <= 0 || h <= 0) {
            return;
        }
        int need = w * h * 3;
        if (packedBgr.length < need) {
            return;
        }

        // 标记是否需要关闭 loading（只关闭一次）
        final boolean shouldCloseLoading = (frame == null);
        final byte[] copy = Arrays.copyOf(packedBgr, need);

        // 检测分辨率是否变化（横竖屏切换）
        final boolean resolutionChanged = (currentWidth != w || currentHeight != h);
        final boolean firstFrame = (currentWidth == 0 && currentHeight == 0);

        SwingUtilities.invokeLater(() -> {
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            byte[] dst = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
            System.arraycopy(copy, 0, dst, 0, need);
            this.frame = bi;
            this.currentWidth = w;
            this.currentHeight = h;

            // 检测到横竖屏切换时，调整窗口大小
            if (!sizeInitialized || resolutionChanged) {
                sizeInitialized = true;
                System.out.println("resize: type=" + (w > h ? "横屏" : "竖屏") + " img=" + w + "x" + h + " panel=" + getWidth() + "x" + getHeight());
                if (ConstService.AUTO_RESIZE_WINDOW) {
                    MainFrame.resizeForContent(w, h);
                } else {
                    setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
                    revalidate();
                }
            }

            repaint();

            // 第一帧渲染完成，关闭 loading 对话框
            if (shouldCloseLoading && loadingDialog != null) {
                loadingDialog.dispose();
                loadingDialog = null;
            }
        });
    }

    /**
     *   @desc : 设置帧（已废弃）
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
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
            // 等比缩放适应窗口（不超过原图尺寸）
            double scale = Math.min(1.0, Math.min((double) pw / iw, (double) ph / ih));
            int scaledW = (int) (iw * scale);
            int scaledH = (int) (ih * scale);
            int dx = (pw - scaledW) / 2;
            int dy = (ph - scaledH) / 2;
            System.out.println("paint: panel=" + pw + "x" + ph + " | img=" + iw + "x" + ih 
                + " | scale=" + String.format("%.4f", scale) 
                + " | draw=" + scaledW + "x" + scaledH + " | offset=" + dx + "," + dy);
            g2.drawImage(img, dx, dy, scaledW, scaledH, null);
        } finally {
            g2.dispose();
        }
    }

}