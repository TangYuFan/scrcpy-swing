package com.tyf.demo.gui;

import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ControlService;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    /** 鼠标是否按下 */
    private volatile boolean mousePressed = false;

    public ContentPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(220, 220, 220));
        setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));

        // 启用鼠标监听
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    mousePressed = true;
                    Point pt = convertToVideoPoint(e.getPoint());
                    if (pt != null) {
                        ControlService.sendTouchDown(pt.x, pt.y);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    mousePressed = false;
                    Point pt = convertToVideoPoint(e.getPoint());
                    if (pt != null) {
                        ControlService.sendTouchUp(pt.x, pt.y);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (mousePressed) {
                    Point pt = convertToVideoPoint(e.getPoint());
                    if (pt != null) {
                        ControlService.sendTouchMove(pt.x, pt.y);
                    }
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    private Point convertToVideoPoint(Point screenPoint) {
        BufferedImage img = frame;
        if (img == null) return null;

        int pw = getWidth();
        int ph = getHeight();
        int iw = img.getWidth();
        int ih = img.getHeight();
        if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) return null;

        double scale = Math.min(1.0, Math.min((double) pw / iw, (double) ph / ih));
        int scaledW = (int) (iw * scale);
        int scaledH = (int) (ih * scale);
        int dx = (pw - scaledW) / 2;
        int dy = (ph - scaledH) / 2;

        int relX = (int) ((screenPoint.x - dx) / scale);
        int relY = (int) ((screenPoint.y - dy) / scale);

        relX = Math.max(0, Math.min(relX, iw - 1));
        relY = Math.max(0, Math.min(relY, ih - 1));

        return new Point(relX, relY);
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
            Logger.warn("scrcpy: incomplete frame data " + packedBgr.length + " < " + need);
            return;
        }

        final boolean shouldCloseLoading = (frame == null);
        final byte[] copy = Arrays.copyOf(packedBgr, need);
        final boolean resolutionChanged = (currentWidth != w || currentHeight != h);
        final boolean firstFrame = (currentWidth == 0 && currentHeight == 0);

        SwingUtilities.invokeLater(() -> {
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            byte[] dst = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
            System.arraycopy(copy, 0, dst, 0, need);

            // 立即更新 frame 引用，防止 paintComponent 读到中间状态
            this.frame = bi;
            this.currentWidth = w;
            this.currentHeight = h;

            if (resolutionChanged) {
                Logger.info("scrcpy: resolution changed " + currentWidth + "x" + currentHeight + " -> " + w + "x" + h);
                ControlService.updateVideoSize(w, h);
            }

            if (!sizeInitialized || resolutionChanged) {
                sizeInitialized = true;
                if (ConstService.AUTO_RESIZE_WINDOW) {
                    MainFrame.resizeForContent(w, h);
                } else {
                    setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
                    revalidate();
                }
            }

            repaint();

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
            double scale = Math.min(1.0, Math.min((double) pw / iw, (double) ph / ih));
            int scaledW = (int) (iw * scale);
            int scaledH = (int) (ih * scale);
            int dx = (pw - scaledW) / 2;
            int dy = (ph - scaledH) / 2;
            g2.drawImage(img, dx, dy, scaledW, scaledH, null);
        } finally {
            g2.dispose();
        }
    }

}