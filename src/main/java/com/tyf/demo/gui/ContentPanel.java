package com.tyf.demo.gui;

import com.tyf.demo.service.AndroidKeyCode;
import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ControlMessage;
import com.tyf.demo.service.ControlService;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *   @desc : 中间内容区（显示手机画面 + 处理输入事件）
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

    /** 点击动画相关 */
    private final List<ClickEffect> clickEffects = new ArrayList<>();
    private ScheduledExecutorService effectExecutor;

    public ContentPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(220, 220, 220));
        setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));

        // 初始化点击效果执行器
        effectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "click-effect");
            t.setDaemon(true);
            return t;
        });

        // 设置焦点，使其可以接收键盘事件
        setFocusable(true);
        requestFocusInWindow();

        // 添加鼠标监听器
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                // Logger.debug("control: mousePressed button=" + e.getButton() + " point=" + p);
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // 左键按下
                    ControlService.sendTouchDown((int) p.getX(), (int) p.getY(), 
                            ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
                    // 添加点击动画效果
                    addClickEffect(e.getPoint());
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // 右键按下
                    ControlService.sendTouchDownRight((int) p.getX(), (int) p.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                // Logger.debug("control: mouseReleased button=" + e.getButton() + " point=" + p);
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // 左键释放
                    ControlService.sendTouchUp((int) p.getX(), (int) p.getY());
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // 右键释放 - 发送返回键
                    ControlService.sendTouchUpRight((int) p.getX(), (int) p.getY());
                    // 右键释放后自动发送返回键
                    ControlService.sendBack();
                }
            }
        });

        // 添加鼠标拖拽监听器
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                // 拖拽时移动触摸
                // Logger.debug("control: mouseDragged point=" + p);
                ControlService.sendTouchMove((int) p.getX(), (int) p.getY());
            }
        });

        // 添加滚轮监听器
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                // 滚轮滚动量：getWheelRotation() 返回鼠标滚轮滚动量
                // Windows 上每次滚动通常返回 1 或 -1（小数表示精确滚动）
                // 乘以 1.5 使得每次滚动约等于滑动一行，数值越大滚动越快
                // 注意：scrcpy 协议中 vScroll > 0 表示向上滚动（Android 行为）
                float vScroll = e.getWheelRotation() * 1.5f;
                // Logger.debug("control: mouseWheel rotation=" + e.getWheelRotation() + " vScroll=" + vScroll + " point=" + p);
                ControlService.sendScroll((int) p.getX(), (int) p.getY(), 0, vScroll);
            }
        });

        // 添加键盘监听器
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (frame == null) return;
                handleKeyPressed(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (frame == null) return;
                handleKeyReleased(e);
            }
        });
    }

    /**
     *   @desc : 将组件坐标转换为设备原始坐标
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param componentPoint : 组件上的坐标
     *   @return 设备原始坐标
     */
    private Point convertToDevicePoint(Point componentPoint) {
        if (frame == null) {
            return componentPoint;
        }

        int iw = frame.getWidth();
        int ih = frame.getHeight();
        int pw = getWidth();
        int ph = getHeight();

        if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) {
            return componentPoint;
        }

        // 计算缩放比例（与 paintComponent 保持一致）
        double scale = Math.min(1.0, Math.min((double) pw / iw, (double) ph / ih));
        int scaledW = (int) (iw * scale);
        int scaledH = (int) (ih * scale);
        int dx = (pw - scaledW) / 2;
        int dy = (ph - scaledH) / 2;

        // 转换坐标
        int x = (int) ((componentPoint.x - dx) / scale);
        int y = (int) ((componentPoint.y - dy) / scale);

        // 边界检查
        x = Math.max(0, Math.min(x, iw - 1));
        y = Math.max(0, Math.min(y, ih - 1));

        return new Point(x, y);
    }

    /**
     *   @desc : 处理键盘按下事件
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    private void handleKeyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        int modifiers = e.getModifiersEx();
        // Logger.debug("control: keyPressed keyCode=" + keyCode + " char=" + e.getKeyChar() + " modifiers=" + modifiers);

        // 快捷键处理
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            switch (keyCode) {
                case KeyEvent.VK_H:
                    // Ctrl+H = Home
                    ControlService.sendHome();
                    e.consume();
                    return;
                case KeyEvent.VK_B:
                case KeyEvent.VK_BACK_SLASH:
                    // Ctrl+B = Back
                    ControlService.sendBack();
                    e.consume();
                    return;
                case KeyEvent.VK_S:
                    // Ctrl+S = 任务切换
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_APP_SWITCH);
                    e.consume();
                    return;
                case KeyEvent.VK_M:
                    // Ctrl+M = 菜单
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_MENU);
                    e.consume();
                    return;
                case KeyEvent.VK_UP:
                    // Ctrl+Up = 音量+
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_VOLUME_UP);
                    e.consume();
                    return;
                case KeyEvent.VK_DOWN:
                    // Ctrl+Down = 音量-
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_VOLUME_DOWN);
                    e.consume();
                    return;
                case KeyEvent.VK_P:
                    // Ctrl+P = 电源键
                    ControlService.sendPower();
                    e.consume();
                    return;
                case KeyEvent.VK_O:
                    // Ctrl+O = 关闭屏幕
                    // TODO: 需要发送屏幕关闭命令
                    e.consume();
                    return;
                case KeyEvent.VK_N:
                    // Ctrl+N = 展开通知面板
                    ControlService.sendExpandNotification();
                    e.consume();
                    return;
                case KeyEvent.VK_F:
                    // Ctrl+F = 全屏切换
                    // TODO: 需要实现全屏切换
                    e.consume();
                    return;
            }
        }

        // Ctrl+Shift+N = 折叠面板
        if ((modifiers & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)) != 0 
                && keyCode == KeyEvent.VK_N) {
            ControlService.sendCollapsePanels();
            e.consume();
            return;
        }

        // 右键菜单快捷键
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_F10) {
            // Shift+F10 = 菜单键
            ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_MENU);
            e.consume();
            return;
        }

        // 功能键映射到 Android 按键
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
                // ESC = 返回
                ControlService.sendBack();
                e.consume();
                return;
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
                // Enter/Space = 点击/确认
                // 发送点击事件（按下然后释放）
                Point center = new Point(currentWidth / 2, currentHeight / 2);
                ControlService.sendTouchDown((int) center.getX(), (int) center.getY(),
                        ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
                try { Thread.sleep(50); } catch (InterruptedException ex) {}
                ControlService.sendTouchUp((int) center.getX(), (int) center.getY());
                e.consume();
                return;
        }

        // 文本输入（无修饰键）
        if (modifiers == 0) {
            char c = e.getKeyChar();
            if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c)) {
                // 发送文本输入
                ControlService.sendText(String.valueOf(c));
                e.consume();
                return;
            }
        }
    }

    /**
     *   @desc : 处理键盘释放事件
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    private void handleKeyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        int modifiers = e.getModifiersEx();

        // 释放 Ctrl+S (任务切换)
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_S) {
            ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_APP_SWITCH);
            e.consume();
            return;
        }

        // 释放 Ctrl+M (菜单)
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_M) {
            ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_MENU);
            e.consume();
            return;
        }

        // 释放 Shift+F10 (菜单键)
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_F10) {
            ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_MENU);
            e.consume();
            return;
        }

        // 释放 Ctrl+Up/Down (音量)
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            if (keyCode == KeyEvent.VK_UP) {
                ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_VOLUME_UP);
                e.consume();
                return;
            }
            if (keyCode == KeyEvent.VK_DOWN) {
                ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_VOLUME_DOWN);
                e.consume();
                return;
            }
        }
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
                // 第一帧渲染成功，连接已建立，显示侧边按钮
                MainPanel.getMainPanel().showSideButtonPanel();
            }

            // 确保焦点在 ContentPanel 上
            requestFocusInWindow();
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

            // 绘制点击效果
            synchronized (clickEffects) {
                for (ClickEffect effect : clickEffects) {
                    effect.draw(g2);
                }
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     *   @desc : 点击效果类 - 模拟器风格的点击涟漪效果
     *   @auth : tyf
     *   @date : 2026-03-21
     */
    private static class ClickEffect {
        /** 圆心坐标 */
        private final int x;
        private final int y;
        /** 最大半径 */
        private final int maxRadius;
        /** 当前半径 */
        private double radius;
        /** 透明度 */
        private double alpha;
        /** 动画进度 */
        private double progress;
        /** 动画是否完成 */
        private boolean finished;

        /** 动画参数 */
        private static final double DURATION = 0.25;  // 动画持续时间（秒）
        private static final double INITIAL_RADIUS = 8;  // 初始半径
        private static final double INITIAL_ALPHA = 0.5;  // 初始透明度

        public ClickEffect(int x, int y) {
            this.x = x;
            this.y = y;
            this.maxRadius = 40;
            this.radius = INITIAL_RADIUS;
            this.alpha = INITIAL_ALPHA;
            this.progress = 0;
            this.finished = false;
        }

        /**
         *   @desc : 更新动画状态
         *   @param deltaProgress : 进度增量（0-1）
         */
        public void update(double deltaProgress) {
            if (finished) return;

            progress += deltaProgress;
            if (progress >= 1.0) {
                progress = 1.0;
                finished = true;
            }

            // 使用缓动函数让动画更自然
            // progress=0: radius=INITIAL_RADIUS, alpha=INITIAL_ALPHA
            // progress=1: radius=maxRadius, alpha=0
            double eased = easeOutQuad(progress);
            radius = INITIAL_RADIUS + (maxRadius - INITIAL_RADIUS) * eased;
            alpha = INITIAL_ALPHA * (1.0 - eased);
        }

        /**
         *   @desc : 缓动函数：二次方缓出
         */
        private double easeOutQuad(double t) {
            return t * (2 - t);
        }

        /**
         *   @desc : 是否已完成动画
         */
        public boolean isFinished() {
            return finished;
        }

        /**
         *   @desc : 绘制效果
         */
        public void draw(Graphics2D g2) {
            if (finished || alpha <= 0) return;

            // 绘制半透明圆形
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
            g2.setColor(Color.WHITE);
            g2.fillOval(x - (int) radius, y - (int) radius, (int) (radius * 2), (int) (radius * 2));

            // 绘制边框
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 255, 255, (int) (alpha * 200)));
            g2.drawOval(x - (int) radius, y - (int) radius, (int) (radius * 2), (int) (radius * 2));

            // 重置混合模式
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    /**
     *   @desc : 添加点击效果
     *   @auth : tyf
     *   @date : 2026-03-21
     *   @param screenPoint : 屏幕坐标（组件上的坐标）
     */
    private void addClickEffect(Point screenPoint) {
        ClickEffect effect = new ClickEffect(screenPoint.x, screenPoint.y);

        synchronized (clickEffects) {
            // 限制同时存在的效果数量（避免快速点击累积）
            while (clickEffects.size() >= 10) {
                clickEffects.remove(0);
            }
            clickEffects.add(effect);
        }

        // 启动动画更新
        effectExecutor.scheduleAtFixedRate(() -> {
            double delta = 0.016; // 约60fps
            boolean needsRepaint = false;

            synchronized (clickEffects) {
                for (ClickEffect e : clickEffects) {
                    e.update(delta);
                    if (!e.isFinished()) {
                        needsRepaint = true;
                    }
                }
                // 移除已完成的效果
                clickEffects.removeIf(ClickEffect::isFinished);
            }

            if (needsRepaint) {
                SwingUtilities.invokeLater(() -> repaint());
            }
        }, 0, 16, TimeUnit.MILLISECONDS);
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
        // 清除所有点击效果
        synchronized (clickEffects) {
            clickEffects.clear();
        }
        // 重新请求焦点
        requestFocusInWindow();
    }

}
