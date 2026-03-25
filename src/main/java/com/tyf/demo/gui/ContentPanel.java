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
    // 游戏模式视角转换的鼠标状态（mouseMoved + mouseDragged 共用）
    private Point gameLastMousePos;
    private boolean gameCursorHidden = false;
    private java.awt.Robot gameRobot;
    private volatile boolean gameMouseListening = true;

    /** Windows 下非 null 时使用 LWJGL 绘制视频；失败或未启用时为 null，回退 CPU 绘制 */
    private LwjglVideoCanvas lwjglVideo;

    /**
     * 映射调试：按设备坐标在渲染画布上显示涟漪。
     * 仅在 LWJGL 渲染路径下生效。
     */
    public void showMappingRippleAtDevicePoint(int deviceX, int deviceY) {
        if (lwjglVideo == null || frame == null) {
            return;
        }
        Rectangle vr = getVideoAreaRect();
        if (vr == null) {
            return;
        }
        int iw = frame.getWidth();
        int ih = frame.getHeight();
        if (iw <= 0 || ih <= 0) {
            return;
        }
        // 与 convertToDevicePoint 反向：device -> local
        double scale = (double) vr.width / (double) iw;
        int lx = (int) Math.round(vr.x + deviceX * scale);
        int ly = (int) Math.round(vr.y + deviceY * scale);
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
                if (GameMappingConfig.isMappingMode()) {
                    SwingUtilities.invokeLater(() -> requestFocusInWindow());
                }
            }
        });

        attachMouseListeners(mouseHost);

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
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        GameMappingConfig.setMappingMode(false);
                        ToolWindow.updateMappingButtonIfExists(false);
                        e.consume();
                        return;
                    }
                    GameMappingService.handleKeyPressed(e.getKeyCode());
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
                    gameLastMousePos = null;
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
                    return;
                }
                Point p = convertToDevicePoint(e.getPoint());
                if (p == null) {
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    ControlService.sendTouchDown((int) p.getX(), (int) p.getY(),
                            ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
                    // 点击波纹：由 LWJGL Canvas 在 OpenGL 中绘制
                    if (lwjglVideo != null) {
                        lwjglVideo.addClickRipple(e.getPoint());
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    ControlService.sendTouchDownRight((int) p.getX(), (int) p.getY());
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
                    return;
                }
                Point p = convertToDevicePoint(e.getPoint());
                if (p == null) {
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    ControlService.sendTouchUp((int) p.getX(), (int) p.getY());
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    ControlService.sendTouchUpRight((int) p.getX(), (int) p.getY());
                    ControlService.sendBack();
                }
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
                            Logger.info("[MAPDBG] mouseDragged local=" + e.getX() + "," + e.getY()
                                    + " last=" + (gameLastMousePos != null ? (gameLastMousePos.x + "," + gameLastMousePos.y) : "null"));
                        }
                    }
                    handleGameModeMouseMotion(e);
                    return;
                }

                if (frame == null) {
                    return;
                }
                Point p = convertToDevicePoint(e.getPoint());
                ControlService.sendTouchMove((int) p.getX(), (int) p.getY());
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
                                    + " last=" + (gameLastMousePos != null ? (gameLastMousePos.x + "," + gameLastMousePos.y) : "null")
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
                gameLastMousePos = null;
            }
        });

        mouseHost.addMouseWheelListener(e -> {
            if (GameMappingConfig.isMappingMode()) {
                return;
            }
            if (frame == null) {
                return;
            }
            Point p = convertToDevicePoint(e.getPoint());
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
            return componentPoint;
        }

        int iw = frame.getWidth();
        int ih = frame.getHeight();
        Dimension surf = resolveRenderSurfaceSize();
        int pw = surf.width;
        int ph = surf.height;

        if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) {
            return componentPoint;
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

        if (gameLastMousePos != null) {
            int deltaX = e.getX() - gameLastMousePos.x;
            int deltaY = e.getY() - gameLastMousePos.y;
            GameMappingService.handleMouseMoved(deltaX, deltaY);
            checkAndWarpMouseInCaptureRect(p);
        }
        gameLastMousePos = p;
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
            gameLastMousePos = new Point(newLocalX, newLocalY);
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
        gameLastMousePos = new Point(x, y);
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
            case KeyEvent.VK_ESCAPE:
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
                    setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
                    revalidate();
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

            requestFocusInWindow();
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
