package com.tyf.demo.gui;

import com.tyf.demo.gui.gl.LwjglVideoCanvas;
import com.tyf.demo.service.AndroidKeyCode;
import com.tyf.demo.service.ConstService;
import com.tyf.demo.service.ControlMessage;
import com.tyf.demo.service.ControlService;
import com.tyf.demo.service.GameMappingConfig;
import com.tyf.demo.service.GameMappingService;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.InputMethodEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContentPanel extends JPanel {

    /** 游戏模式鼠标捕获区占视频区域比例（宽/高），用于限制在中心小区域内循环 */
    private static final double MOUSE_CAPTURE_RATIO = 0.80;

    private volatile BufferedImage frame;
    private static JDialog loadingDialog;
    private volatile int currentWidth;
    private volatile int currentHeight;
    private volatile boolean sizeInitialized = false;
    private BufferedImage renderImage;
    private byte[] renderImageBytes;

    // 旧 Swing 点击波纹特效已移除（LWJGL Canvas 输出视频时无法被轻量级覆盖）
    private boolean gameCursorHidden = false;
    private java.awt.Robot gameRobot;
    private volatile boolean gameMouseListening = true;

    /** 记录上一次触摸按下的设备坐标，用于在释放时坐标转换失败时的 fallback */
    private volatile int lastTouchDownX = -1;
    private volatile int lastTouchDownY = -1;

    /** Windows 下非 null 时使用 LWJGL 绘制视频；失败或未启用时为 null，回退 CPU 绘制 */
    private LwjglVideoCanvas lwjglVideo;

    // -------- 普通模式滑屏兜底：避免 mouseReleased 丢失导致“按住不松”--------
    private static final AtomicBoolean NORMAL_RELEASE_HOOK_INSTALLED = new AtomicBoolean(false);
    private volatile boolean normalTouchActive = false;
    private volatile int normalTouchButton = 0;
    /** 排查滑屏/抬起：设为 true 时输出 [NORMAL_TOUCH] 日志，确认后可改回 false */
    private static final boolean NORMAL_TOUCH_DEBUG = true;
    private static volatile int normalTouchSeq;
    private static volatile long lastNormalDragLogMs;

    private static void logNormalTouch(String msg) {
        if (NORMAL_TOUCH_DEBUG) {
            Logger.info("[NORMAL_TOUCH] " + msg);
        }
    }

    /**
     * 映射调试：按设备坐标在渲染画布上显示涟漪。
     * 仅在 LWJGL 渲染路径下生效。
     * 使用与 paintGL() 一致的坐标计算方式，确保涟漪位置准确。
     */
    public void showMappingRippleAtDevicePoint(int deviceX, int deviceY) {
        if (lwjglVideo == null || frame == null) {
            return;
        }
        int iw = frame.getWidth();
        int ih = frame.getHeight();
        if (iw <= 0 || ih <= 0) {
            return;
        }
        Dimension surf = resolveRenderSurfaceSize();
        int canvasW = surf.width;
        int canvasH = surf.height;
        if (canvasW <= 0 || canvasH <= 0) {
            return;
        }
        double videoAspect = (double) iw / (double) ih;
        double viewAspect = (double) canvasW / (double) canvasH;
        double drawW, drawH;
        if (videoAspect > viewAspect) {
            drawW = canvasW;
            drawH = canvasW / videoAspect;
        } else {
            drawH = canvasH;
            drawW = canvasH * videoAspect;
        }
        double x0 = (canvasW - drawW) / 2.0;
        double y0 = (canvasH - drawH) / 2.0;
        double scale = drawW / (double) iw;
        int lx = (int) Math.round(x0 + deviceX * scale);
        int ly = (int) Math.round(y0 + deviceY * scale);
        lwjglVideo.addClickRipple(new Point(lx, ly));
    }

    // -------- 映射排查日志（节流，避免刷屏）--------
    private static final boolean MAP_DEBUG = false;
    private static final long MAP_DEBUG_MOVE_LOG_INTERVAL_MS = 80L;
    private static volatile long lastMapMoveLogMs;

    private Dimension resolveRenderSurfaceSize() {
        if (lwjglVideo != null) {
            return new Dimension(Math.max(1, lwjglVideo.getWidth()), Math.max(1, lwjglVideo.getHeight()));
        }
        return new Dimension(Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    /**
     * GLFW 捕获窗口：覆盖在视频画布上获取输入。
     */
    public Rectangle getVideoSurfaceBoundsOnScreen() {
        try {
            Component c = lwjglVideo != null ? lwjglVideo : this;
            Point p = c.getLocationOnScreen();
            return new Rectangle(p.x, p.y, c.getWidth(), c.getHeight());
        } catch (Exception e) {
            return null;
        }
    }

    public void enableRawInputIfPossible() {
        // 已切换为 GLFW 输入捕获；Raw Input 方案保留但默认不启用
    }

    public ContentPanel() {
        setLayout(new BorderLayout());
        setBackground(ConstService.THEME_CONTENT_BG);
        setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
        // 游戏模式按键不应进入输入法通道（WASD 触发拼音候选等）
        setFocusTraversalKeysEnabled(false);
        enableInputMethods(false);

        Component mouseHost = this;
        if (isWindowsOs() && isGpuVideoEnabled()) {
            try {
                // 方案一：只嵌入重量级 Canvas，不做任何 Swing overlay 叠加
                lwjglVideo = new LwjglVideoCanvas();
                lwjglVideo.setFocusable(false);
                add(lwjglVideo, BorderLayout.CENTER);
                mouseHost = lwjglVideo;
            } catch (Throwable t) {
                Logger.warn(t, "LWJGL 视频画布不可用，使用 CPU 绘制");
                lwjglVideo = null;
            }
        } else {
        }

        setFocusable(true);
        requestFocusInWindow();

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // 游戏模式下输入已由 GLFW 捕获窗口接管，这里不要再“抢回”Swing焦点，
                // 否则会与 GLFW 争抢前台/焦点，可能触发窗口抖动/尺寸跳变。
            }
        });

        attachMouseListeners(mouseHost);
        installNormalModeGlobalReleaseHookIfNeeded();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    if (!hasFocus()) {
                        requestFocusInWindow();
                    }
                    if (MAP_DEBUG) {
                        Logger.info("[MAPDBG] keyPressed keyCode=" + e.getKeyCode()
                                + " focus=" + hasFocus()
                                + " host=" + (lwjglVideo != null ? "LWJGL" : "CPU"));
                    }
                    if (e.getKeyCode() == KeyEvent.VK_TAB) {
                        GameMappingConfig.setMappingMode(false);
                        ToolWindow.updateMappingButtonIfExists(false);
                        e.consume();
                        return;
                    }
                    GameMappingService.handleKeyPressed(e.getKeyCode());
                    e.consume();
                    return;
                }

                // 普通模式：ESC 切换到游戏模式
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    GameMappingConfig.setMappingMode(true);
                    ToolWindow.updateMappingButtonIfExists(true);
                    e.consume();
                    return;
                }

                if (frame == null) return;
                handleKeyPressed(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    if (MAP_DEBUG) {
                        Logger.info("[MAPDBG] keyReleased keyCode=" + e.getKeyCode());
                    }
                    GameMappingService.handleKeyReleased(e.getKeyCode());
                    e.consume();
                    return;
                }

                if (frame == null) return;
                handleKeyReleased(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // 游戏模式下吞掉字符事件，避免触发输入法候选
                if (GameMappingConfig.isMappingMode()) {
                    e.consume();
                }
            }
        });
    }

    /**
     * @desc : 普通模式下，全局兜底 mouseReleased，防止拖动/移出窗口时 release 丢失导致手机端一直按住
     */
    private void installNormalModeGlobalReleaseHookIfNeeded() {
        if (!NORMAL_RELEASE_HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent)) {
                return;
            }
            MouseEvent me = (MouseEvent) event;
            if (me.getID() != MouseEvent.MOUSE_RELEASED) {
                return;
            }
            // 仅普通模式需要兜底；游戏模式 release 由 GLFW 处理
            if (GameMappingConfig.isMappingMode()) {
                return;
            }
            if (!normalTouchActive) {
                return;
            }
            Object src = me.getSource();
            String srcName = src == null ? "null" : src.getClass().getSimpleName();
            final int seqSnapshot = normalTouchSeq;
            final int btn = me.getButton();
            final int sx = me.getXOnScreen();
            final int sy = me.getYOnScreen();
            // 全局监听可能在目标组件的 mouseReleased 之前触发；延后一帧再兜底，
            // 若本地已正常抬起则 normalTouchActive 已为 false，避免重复 sendTouchUp。
            SwingUtilities.invokeLater(() -> {
                if (GameMappingConfig.isMappingMode() || !normalTouchActive) {
                    return;
                }
                int x = lastTouchDownX;
                int y = lastTouchDownY;
                logNormalTouch("fallbackRelease seq=" + seqSnapshot
                        + " source=" + srcName
                        + " button=" + btn
                        + " screen=" + sx + "," + sy
                        + " lastDevice=" + x + "," + y
                        + " normalBtn=" + normalTouchButton);
                try {
                    if (x >= 0 && y >= 0) {
                        if (normalTouchButton == MouseEvent.BUTTON3) {
                            ControlService.sendTouchUpRight(x, y);
                            logNormalTouch("fallbackRelease -> sendTouchUpRight device=" + x + "," + y);
                        } else {
                            ControlService.sendTouchUp(x, y);
                            logNormalTouch("fallbackRelease -> sendTouchUp device=" + x + "," + y);
                        }
                    } else {
                        logNormalTouch("fallbackRelease -> skip send (invalid lastDevice), state cleared");
                    }
                } finally {
                    lastTouchDownX = -1;
                    lastTouchDownY = -1;
                    normalTouchActive = false;
                    normalTouchButton = 0;
                }
            });
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    private static boolean isWindowsOs() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isGpuVideoEnabled() {
        // 当前仅 Windows 目标，优先走 GPU 路径；失败会自动回退 CPU
        return isWindowsOs();
    }

    private void attachMouseListeners(Component mouseHost) {
        mouseHost.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    // AWT 仅负责捕获/回绕，视角相对位移由 Raw Input 提供
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    ContentPanel.this.requestFocusInWindow();
                    if (MAP_DEBUG) {
                        Logger.info("[MAPDBG] mousePressed button=" + e.getButton()
                                + " local=" + e.getX() + "," + e.getY()
                                + " host=" + mouseHost.getClass().getSimpleName() + " " + mouseHost.getWidth() + "x" + mouseHost.getHeight()
                                + " frame=" + (frame != null ? (frame.getWidth() + "x" + frame.getHeight()) : "null"));
                        Rectangle vr = getVideoAreaRect();
                        Logger.info("[MAPDBG] videoRect=" + (vr != null ? (vr.x + "," + vr.y + " " + vr.width + "x" + vr.height) : "null"));
                    }
                    GameMappingService.handleMousePressed(e.getButton());
                    return;
                }

                if (frame == null) {
                    logNormalTouch("mousePressed skip: frame=null");
                    return;
                }
                Point p = convertToDevicePointClamped(e.getPoint());
                if (p == null) {
                    logNormalTouch("mousePressed skip: convert=null button=" + e.getButton()
                            + " local=" + e.getX() + "," + e.getY());
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    int x = (int) p.getX();
                    int y = (int) p.getY();
                    lastTouchDownX = x;
                    lastTouchDownY = y;
                    normalTouchActive = true;
                    normalTouchButton = MouseEvent.BUTTON1;
                    normalTouchSeq++;
                    logNormalTouch("down seq=" + normalTouchSeq + " btn=LEFT device=" + x + "," + y
                            + " local=" + e.getX() + "," + e.getY()
                            + " host=" + mouseHost.getClass().getSimpleName());
                    ControlService.sendTouchDown(x, y,
                            ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
                    // 点击波纹：由 LWJGL Canvas 在 OpenGL 中绘制
                    if (lwjglVideo != null) {
                        lwjglVideo.addClickRipple(e.getPoint());
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    int x = (int) p.getX();
                    int y = (int) p.getY();
                    lastTouchDownX = x;
                    lastTouchDownY = y;
                    normalTouchActive = true;
                    normalTouchButton = MouseEvent.BUTTON3;
                    normalTouchSeq++;
                    logNormalTouch("down seq=" + normalTouchSeq + " btn=RIGHT device=" + x + "," + y
                            + " local=" + e.getX() + "," + e.getY()
                            + " host=" + mouseHost.getClass().getSimpleName());
                    ControlService.sendTouchDownRight(x, y);
                } else {
                    logNormalTouch("mousePressed ignored button=" + e.getButton());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    if (MAP_DEBUG) {
                        Logger.info("[MAPDBG] mouseReleased button=" + e.getButton()
                                + " local=" + e.getX() + "," + e.getY());
                    }
                    GameMappingService.handleMouseReleased(e.getButton());
                    return;
                }

                if (frame == null) {
                    logNormalTouch("mouseReleased skip: frame=null seq=" + normalTouchSeq);
                    return;
                }
                boolean rawOutsideVideo = !isPointInVideoArea(e.getPoint());
                Point p = convertToDevicePointClamped(e.getPoint());
                if (NORMAL_TOUCH_DEBUG && p != null && rawOutsideVideo) {
                    logNormalTouch("mouseReleased seq=" + normalTouchSeq
                            + " releaseInLetterbox local=" + e.getX() + "," + e.getY()
                            + " -> clampedDevice=" + p.x + "," + p.y);
                }
                int x, y;
                if (p == null) {
                    // 坐标转换失败（鼠标在视频区域外释放），使用上次按下的坐标作为 fallback
                    x = lastTouchDownX;
                    y = lastTouchDownY;
                    if (x < 0 || y < 0) {
                        logNormalTouch("mouseReleased ABORT seq=" + normalTouchSeq
                                + " convert=null & no fallback local=" + e.getX() + "," + e.getY()
                                + " btn=" + e.getButton()
                                + " (touch may stay down until global hook)");
                        return;
                    }
                    logNormalTouch("mouseReleased seq=" + normalTouchSeq + " useFallbackDevice=" + x + "," + y
                            + " local=" + e.getX() + "," + e.getY());
                } else {
                    x = (int) p.getX();
                    y = (int) p.getY();
                    logNormalTouch("mouseReleased seq=" + normalTouchSeq + " path=local device=" + x + "," + y
                            + " local=" + e.getX() + "," + e.getY() + " btn=" + e.getButton());
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    ControlService.sendTouchUp(x, y);
                    logNormalTouch("localRelease -> sendTouchUp device=" + x + "," + y);
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    ControlService.sendTouchUpRight(x, y);
                    ControlService.sendBack();
                    logNormalTouch("localRelease -> sendTouchUpRight+Back device=" + x + "," + y);
                } else {
                    logNormalTouch("mouseReleased no touchUp for button=" + e.getButton()
                            + " (normalTouchActive was " + normalTouchActive + ")");
                }
                // 清除记录的坐标
                lastTouchDownX = -1;
                lastTouchDownY = -1;
                normalTouchActive = false;
                normalTouchButton = 0;
            }
        });

        mouseHost.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    if (MAP_DEBUG) {
                        long now = System.currentTimeMillis();
                        if (now - lastMapMoveLogMs >= MAP_DEBUG_MOVE_LOG_INTERVAL_MS) {
                            lastMapMoveLogMs = now;
                            Logger.info("[MAPDBG] mouseDragged local=" + e.getX() + "," + e.getY());
                        }
                    }
                    handleGameModeMouseMotion(e);
                    return;
                }

                if (frame == null) {
                    return;
                }
                Point p = convertToDevicePointClamped(e.getPoint());
                int x, y;
                if (p == null) {
                    // 坐标转换失败（拖出视频区域），使用上次有效坐标
                    x = lastTouchDownX;
                    y = lastTouchDownY;
                    if (x < 0 || y < 0) {
                        return;
                    }
                } else {
                    x = (int) p.getX();
                    y = (int) p.getY();
                    // 更新最后有效坐标
                    lastTouchDownX = x;
                    lastTouchDownY = y;
                }
                ControlService.sendTouchMove(x, y);
                if (NORMAL_TOUCH_DEBUG) {
                    long now = System.currentTimeMillis();
                    if (now - lastNormalDragLogMs >= 120L) {
                        lastNormalDragLogMs = now;
                        logNormalTouch("drag seq=" + normalTouchSeq + " move device=" + x + "," + y
                                + " local=" + e.getX() + "," + e.getY()
                                + " pNull=" + (p == null));
                    }
                }
            }
        });

        mouseHost.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    if (MAP_DEBUG) {
                        long now = System.currentTimeMillis();
                        if (now - lastMapMoveLogMs >= MAP_DEBUG_MOVE_LOG_INTERVAL_MS) {
                            lastMapMoveLogMs = now;
                            Logger.info("[MAPDBG] mouseMoved local=" + e.getX() + "," + e.getY()
                                    + " inVideo=" + isPointInVideoArea(e.getPoint()));
                        }
                    }
                    handleGameModeMouseMotion(e);
                    return;
                }

                if (gameCursorHidden) {
                    ContentPanel.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    gameCursorHidden = false;
                }
                // 旧版 AWT delta 逻辑已移除
            }
        });

        mouseHost.addMouseWheelListener(e -> {
            if (GameMappingConfig.isMappingMode()) {
                return;
            }
            if (frame == null) {
                return;
            }
            Point p = convertToDevicePointClamped(e.getPoint());
            if (p == null) {
                return;
            }
            float vScroll = -e.getWheelRotation() * 0.1f;
            ControlService.sendScroll((int) p.getX(), (int) p.getY(), 0, vScroll);
        });
    }

    @Override
    protected void processInputMethodEvent(InputMethodEvent e) {
        // 游戏模式下禁用输入法组合输入，避免 WASD 等触发候选框
        if (GameMappingConfig.isMappingMode()) {
            e.consume();
            return;
        }
        super.processInputMethodEvent(e);
    }

    private Point convertToDevicePoint(Point componentPoint) {
        if (frame == null) {
            return null;
        }

        int iw = frame.getWidth();
        int ih = frame.getHeight();
        Dimension surf = resolveRenderSurfaceSize();
        int pw = surf.width;
        int ph = surf.height;

        if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) {
            return null;
        }

        double scale = Math.min(1.0, Math.min((double) pw / iw, (double) ph / ih));
        int scaledW = (int) (iw * scale);
        int scaledH = (int) (ih * scale);
        int dx = (pw - scaledW) / 2;
        int dy = (ph - scaledH) / 2;

        if (componentPoint.x < dx || componentPoint.x >= dx + scaledW ||
            componentPoint.y < dy || componentPoint.y >= dy + scaledH) {
            return null;
        }

        int x = (int) ((componentPoint.x - dx) / scale);
        int y = (int) ((componentPoint.y - dy) / scale);

        x = Math.max(0, Math.min(x, iw - 1));
        y = Math.max(0, Math.min(y, ih - 1));

        return new Point(x, y);
    }

    /**
     * @desc : 先将本地坐标钳制到视频可视矩形再映射到设备坐标，避免释放在黑边/外时 convert 为 null 只能依赖 lastTouchDown
     * @auth : tyf
     * @date : 2026-03-26
     */
    private Point convertToDevicePointClamped(Point componentPoint) {
        Point p = convertToDevicePoint(componentPoint);
        if (p != null) {
            return p;
        }
        Rectangle vr = getVideoAreaRect();
        if (vr == null) {
            return null;
        }
        int lx = Math.max(vr.x, Math.min(vr.x + vr.width - 1, componentPoint.x));
        int ly = Math.max(vr.y, Math.min(vr.y + vr.height - 1, componentPoint.y));
        return convertToDevicePoint(new Point(lx, ly));
    }

    private boolean isPointInVideoArea(Point componentPoint) {
        Rectangle vr = getVideoAreaRect();
        if (vr == null) {
            return false;
        }
        return componentPoint.x >= vr.x && componentPoint.x < vr.x + vr.width
                && componentPoint.y >= vr.y && componentPoint.y < vr.y + vr.height;
    }

    private Rectangle getVideoAreaRect() {
        if (frame == null) {
            return null;
        }
        int iw = frame.getWidth();
        int ih = frame.getHeight();
        Dimension surf = resolveRenderSurfaceSize();
        int pw = surf.width;
        int ph = surf.height;
        if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) {
            return null;
        }
        double scale = Math.min(1.0, Math.min((double) pw / iw, (double) ph / ih));
        int scaledW = (int) (iw * scale);
        int scaledH = (int) (ih * scale);
        int dx = (pw - scaledW) / 2;
        int dy = (ph - scaledH) / 2;
        return new Rectangle(dx, dy, scaledW, scaledH);
    }

    /**
     * @desc : 获取游戏模式鼠标捕获区域（视频区域中心的一小块）
     * @auth : tyf
     * @date : 2026-03-20
     */
    private Rectangle getMouseCaptureRect() {
        Rectangle vr = getVideoAreaRect();
        if (vr == null) {
            return null;
        }
        int capW = Math.max(120, (int) (vr.width * MOUSE_CAPTURE_RATIO));
        int capH = Math.max(120, (int) (vr.height * MOUSE_CAPTURE_RATIO));
        capW = Math.min(capW, vr.width);
        capH = Math.min(capH, vr.height);

        int capX = vr.x + (vr.width - capW) / 2;
        int capY = vr.y + (vr.height - capH) / 2;
        return new Rectangle(capX, capY, capW, capH);
    }

    private void ensureGameRobot() {
        if (gameRobot != null) {
            return;
        }
        try {
            gameRobot = new java.awt.Robot();
        } catch (Exception ex) {
            gameRobot = null;
        }
    }

    private void handleGameModeMouseMotion(MouseEvent e) {
        if (!gameMouseListening) {
            return;
        }

        if (!gameCursorHidden) {
            setCursor(createTransparentCursor());
            gameCursorHidden = true;
        }
        ensureGameRobot();

        Point p = e.getPoint();
        // 在黑边等非视频区域时原先直接 return，既不回绕也不更新 lastPos，易导致失控与下次巨大 delta
        if (!isPointInVideoArea(p)) {
            if (gameRobot != null) {
                warpToCaptureSafeArea();
            }
            return;
        }

        // 视角移动由 Windows Raw Input（JNA）提供相对位移；AWT 仅负责回绕保持鼠标在捕获区
        checkAndWarpMouseInCaptureRect(p);
    }

    /**
     * 捕获区边缘“安全带”宽度：随窗口变大，快速甩动时仍有机会在单帧内触发回绕。
     */
    private static int resolveCaptureMargin(Rectangle vr) {
        int minSide = Math.max(1, Math.min(vr.width, vr.height));
        // 约 6%～25% 边长，且不少于 52px，避免单帧跨过边缘带
        int m = Math.max(52, minSide / 6);
        m = Math.min(m, minSide / 3 - 2);
        return Math.max(40, m);
    }

    private void checkAndWarpMouseInCaptureRect(Point localPos) {
        if (gameRobot == null) {
            return;
        }
        Rectangle vr = getMouseCaptureRect();
        if (vr == null) {
            return;
        }
        int margin = resolveCaptureMargin(vr);
        boolean needsWarp = false;
        int newLocalX = localPos.x;
        int newLocalY = localPos.y;

        if (!vr.contains(localPos.x, localPos.y)) {
            needsWarp = true;
            newLocalX = vr.x + Math.max(margin, vr.width / 2);
            newLocalY = vr.y + Math.max(margin, vr.height / 2);
            newLocalX = Math.min(vr.x + vr.width - margin, newLocalX);
            newLocalY = Math.min(vr.y + vr.height - margin, newLocalY);
        } else {
            if (localPos.x <= vr.x + margin) {
                newLocalX = vr.x + vr.width - margin;
                needsWarp = true;
            } else if (localPos.x >= vr.x + vr.width - margin) {
                newLocalX = vr.x + margin;
                needsWarp = true;
            }

            if (localPos.y <= vr.y + margin) {
                newLocalY = vr.y + vr.height - margin;
                needsWarp = true;
            } else if (localPos.y >= vr.y + vr.height - margin) {
                newLocalY = vr.y + margin;
                needsWarp = true;
            }
        }

        if (needsWarp) {
            gameMouseListening = false;
            Point screen = new Point(newLocalX, newLocalY);
            SwingUtilities.convertPointToScreen(screen, lwjglVideo != null ? lwjglVideo : this);
            gameRobot.mouseMove(screen.x, screen.y);
            gameMouseListening = true;
            GameMappingService.ignoreNextMouseMoves(5);
        }
    }

    private void warpToCaptureSafeArea() {
        ensureGameRobot();
        if (gameRobot == null) {
            return;
        }
        Rectangle vr = getMouseCaptureRect();
        if (vr == null) {
            return;
        }
        int margin = resolveCaptureMargin(vr);
        int x = vr.x + Math.max(margin, vr.width / 2);
        int y = vr.y + Math.max(margin, vr.height / 2);
        x = Math.min(vr.x + vr.width - margin, x);
        y = Math.min(vr.y + vr.height - margin, y);
        Point screen = new Point(x, y);
        SwingUtilities.convertPointToScreen(screen, lwjglVideo != null ? lwjglVideo : this);
        gameMouseListening = false;
        gameRobot.mouseMove(screen.x, screen.y);
        gameMouseListening = true;
        GameMappingService.ignoreNextMouseMoves(5);
    }

    private void handleKeyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        int modifiers = e.getModifiersEx();

        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            switch (keyCode) {
                case KeyEvent.VK_H:
                    ControlService.sendHome();
                    e.consume();
                    return;
                case KeyEvent.VK_B:
                case KeyEvent.VK_BACK_SLASH:
                    ControlService.sendBack();
                    e.consume();
                    return;
                case KeyEvent.VK_S:
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_APP_SWITCH);
                    e.consume();
                    return;
                case KeyEvent.VK_M:
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_MENU);
                    e.consume();
                    return;
                case KeyEvent.VK_UP:
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_VOLUME_UP);
                    e.consume();
                    return;
                case KeyEvent.VK_DOWN:
                    ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_VOLUME_DOWN);
                    e.consume();
                    return;
                case KeyEvent.VK_P:
                    ControlService.sendPower();
                    e.consume();
                    return;
                case KeyEvent.VK_O:
                    e.consume();
                    return;
                case KeyEvent.VK_N:
                    ControlService.sendExpandNotification();
                    e.consume();
                    return;
                case KeyEvent.VK_F:
                    e.consume();
                    return;
            }
        }

        if ((modifiers & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)) != 0 
                && keyCode == KeyEvent.VK_N) {
            ControlService.sendCollapsePanels();
            e.consume();
            return;
        }

        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_F10) {
            ControlService.sendKeyDown(AndroidKeyCode.KEYCODE_MENU);
            e.consume();
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_TAB:
                ControlService.sendBack();
                e.consume();
                return;
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
                Point center = new Point(currentWidth / 2, currentHeight / 2);
                ControlService.sendTouchDown((int) center.getX(), (int) center.getY(),
                        ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
                try { Thread.sleep(50); } catch (InterruptedException ex) {}
                ControlService.sendTouchUp((int) center.getX(), (int) center.getY());
                e.consume();
                return;
        }

        if (modifiers == 0) {
            char c = e.getKeyChar();
            if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c)) {
                ControlService.sendText(String.valueOf(c));
                e.consume();
                return;
            }
        }
    }

    private void handleKeyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        int modifiers = e.getModifiersEx();

        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_S) {
            ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_APP_SWITCH);
            e.consume();
            return;
        }

        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_M) {
            ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_MENU);
            e.consume();
            return;
        }

        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && keyCode == KeyEvent.VK_F10) {
            ControlService.sendKeyUp(AndroidKeyCode.KEYCODE_MENU);
            e.consume();
            return;
        }

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

    public static void closeLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog.dispose();
            loadingDialog = null;
        }
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public BufferedImage getCurrentFrame() {
        return frame;
    }

    public boolean hasValidFrame() {
        return frame != null && currentWidth > 0 && currentHeight > 0;
    }

    public static void setLoadingDialog(JDialog dialog) {
        loadingDialog = dialog;
    }

    private void ensureRenderImageBuffer(int w, int h) {
        if (renderImage == null || renderImage.getWidth() != w || renderImage.getHeight() != h) {
            renderImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            renderImageBytes = ((DataBufferByte) renderImage.getRaster().getDataBuffer()).getData();
        }
    }

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
        final boolean resolutionChanged = (currentWidth != w || currentHeight != h);

        SwingUtilities.invokeLater(() -> {
            if (shouldCloseLoading) {
                Logger.info("渲染模式: " + (lwjglVideo != null ? "GPU(LWJGL)" : "CPU(Swing)"));
            }
            ensureRenderImageBuffer(w, h);
            System.arraycopy(packedBgr, 0, renderImageBytes, 0, need);

            this.frame = renderImage;
            this.currentWidth = w;
            this.currentHeight = h;

            GameMappingService.updateVideoSize(w, h);
            ControlService.updateVideoSize(w, h);

            if (resolutionChanged) {
                Logger.info("scrcpy: resolution changed " + currentWidth + "x" + currentHeight + " -> " + w + "x" + h);
            }

            if (!sizeInitialized || resolutionChanged) {
                sizeInitialized = true;
                if (ConstService.AUTO_RESIZE_WINDOW) {
                    MainFrame.resizeForContent(w, h);
                } else {
                    // 关闭自动调整窗口大小时，不再在收到帧时改 preferredSize/revalidate，
                    // 否则用户手动调整过窗口后，可能在进入游戏模式等时机出现“尺寸跳变”。
                }
            }

            if (lwjglVideo != null) {
                lwjglVideo.submitFrame(renderImageBytes, w, h);
                if (!lwjglVideo.isRenderHealthy()) {
                    Logger.warn("LWJGL 渲染不可用，自动回退 CPU 绘制");
                    lwjglVideo = null;
                    removeAll();
                    setLayout(new BorderLayout());
                    attachMouseListeners(this);
                    revalidate();
                    repaint();
                }
            } else {
                repaint();
            }

            if (shouldCloseLoading && loadingDialog != null) {
                loadingDialog.dispose();
                loadingDialog = null;
            }

            // 游戏模式下由 GLFW 捕获窗口负责焦点与输入，不要每帧抢焦点
            if (!GameMappingConfig.isMappingMode()) {
                requestFocusInWindow();
            }
        });
    }

    @Deprecated
    public void setFrame(BufferedImage img) {
        this.frame = img;
        SwingUtilities.invokeLater(this::repaint);
    }

    // 旧版 Swing 画视频/点击波纹已移除：当前视频由 `LwjglVideoCanvas` 绘制。

    public void reset() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::reset);
            return;
        }
        sizeInitialized = false;
        currentWidth = 0;
        currentHeight = 0;
        frame = null;
        if (lwjglVideo != null) {
            lwjglVideo.clearVideo();
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        requestFocusInWindow();
        repaint();
    }

    private Cursor createTransparentCursor() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        return Toolkit.getDefaultToolkit().createCustomCursor(img, new Point(0, 0), "hidden");
    }

}
