package com.tyf.demo.gui;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentPanel extends JPanel {

    /** 游戏模式鼠标捕获区占视频区域比例（宽/高），用于限制在中心小区域内循环 */
    private static final double MOUSE_CAPTURE_RATIO = 0.38;

    private volatile BufferedImage frame;
    private static JDialog loadingDialog;
    private volatile int currentWidth;
    private volatile int currentHeight;
    private volatile boolean sizeInitialized = false;
    private BufferedImage renderImage;
    private byte[] renderImageBytes;

    private final List<ClickEffect> clickEffects = new ArrayList<>();
    private ScheduledExecutorService effectExecutor;
    // 游戏模式视角转换的鼠标状态（mouseMoved + mouseDragged 共用）
    private Point gameLastMousePos;
    private boolean gameCursorHidden = false;
    private java.awt.Robot gameRobot;

    public ContentPanel() {
        setLayout(new BorderLayout());
        setBackground(ConstService.THEME_CONTENT_BG);
        setPreferredSize(new Dimension(ConstService.MAIN_WIDTH, ConstService.MAIN_HEIGHT));
        // 游戏模式按键不应进入输入法通道（WASD 触发拼音候选等）
        setFocusTraversalKeysEnabled(false);
        enableInputMethods(false);

        effectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "click-effect");
            t.setDaemon(true);
            return t;
        });

        effectExecutor.scheduleAtFixedRate(() -> {
            double delta = 0.016;
            boolean needsRepaint = false;

            synchronized (clickEffects) {
                for (ClickEffect e : clickEffects) {
                    e.update(delta);
                    if (!e.isFinished()) {
                        needsRepaint = true;
                    }
                }
                clickEffects.removeIf(ClickEffect::isFinished);
            }

            if (needsRepaint) {
                SwingUtilities.invokeLater(() -> repaint());
            }
        }, 0, 16, TimeUnit.MILLISECONDS);

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

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    requestFocusInWindow();
                    GameMappingService.handleMousePressed(e.getButton());
                    return;
                }

                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                if (p == null) return;
                if (e.getButton() == MouseEvent.BUTTON1) {
                    ControlService.sendTouchDown((int) p.getX(), (int) p.getY(), 
                            ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
                    addClickEffect(e.getPoint());
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    ControlService.sendTouchDownRight((int) p.getX(), (int) p.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    GameMappingService.handleMouseReleased(e.getButton());
                    return;
                }

                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                if (p == null) return;
                if (e.getButton() == MouseEvent.BUTTON1) {
                    ControlService.sendTouchUp((int) p.getX(), (int) p.getY());
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    ControlService.sendTouchUpRight((int) p.getX(), (int) p.getY());
                    ControlService.sendBack();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    handleGameModeMouseMotion(e);
                    return;
                }

                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                ControlService.sendTouchMove((int) p.getX(), (int) p.getY());
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    handleGameModeMouseMotion(e);
                    return;
                }

                if (gameCursorHidden) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    gameCursorHidden = false;
                }
                gameLastMousePos = null;
            }
        });

        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    return;
                }
                if (frame == null) return;
                Point p = convertToDevicePoint(e.getPoint());
                if (p == null) return;
                float vScroll = -e.getWheelRotation() * 0.1f;
                ControlService.sendScroll((int) p.getX(), (int) p.getY(), 0, vScroll);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (GameMappingConfig.isMappingMode()) {
                    if (!hasFocus()) {
                        requestFocusInWindow();
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
        int pw = getWidth();
        int ph = getHeight();

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
        int pw = getWidth();
        int ph = getHeight();
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

    private void handleGameModeMouseMotion(MouseEvent e) {
        if (!gameCursorHidden) {
            setCursor(createTransparentCursor());
            gameCursorHidden = true;
            try {
                gameRobot = new java.awt.Robot();
            } catch (Exception ex) {
                gameRobot = null;
            }
        }

        if (!isPointInVideoArea(e.getPoint())) {
            warpToCaptureSafeArea();
            gameLastMousePos = null;
            return;
        }

        if (gameLastMousePos != null) {
            int deltaX = e.getX() - gameLastMousePos.x;
            int deltaY = e.getY() - gameLastMousePos.y;
            GameMappingService.handleMouseMoved(deltaX, deltaY);
            checkAndWarpMouseInCaptureRect(e.getPoint());
        }
        gameLastMousePos = e.getPoint();
    }

    private void checkAndWarpMouseInCaptureRect(Point localPos) {
        if (gameRobot == null) {
            return;
        }
        int margin = 30;
        boolean needsWarp = false;
        int newLocalX = localPos.x;
        int newLocalY = localPos.y;
        Rectangle vr = getMouseCaptureRect();
        if (vr == null) {
            return;
        }

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

        if (needsWarp) {
            Point p = new Point(newLocalX, newLocalY);
            SwingUtilities.convertPointToScreen(p, this);
            gameRobot.mouseMove(p.x, p.y);
            gameLastMousePos = null;
            GameMappingService.ignoreNextMouseMoves(3);
        }
    }

    private void warpToCaptureSafeArea() {
        if (gameRobot == null) {
            return;
        }
        Rectangle vr = getMouseCaptureRect();
        if (vr == null) {
            return;
        }
        int margin = 30;
        int x = vr.x + Math.max(margin, vr.width / 2);
        int y = vr.y + Math.max(margin, vr.height / 2);
        x = Math.min(vr.x + vr.width - margin, x);
        y = Math.min(vr.y + vr.height - margin, y);
        Point p = new Point(x, y);
        SwingUtilities.convertPointToScreen(p, this);
        gameRobot.mouseMove(p.x, p.y);
        gameLastMousePos = null;
        GameMappingService.ignoreNextMouseMoves(4);
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

            repaint();

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

            synchronized (clickEffects) {
                for (ClickEffect effect : clickEffects) {
                    effect.draw(g2);
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static class ClickEffect {
        private final int x;
        private final int y;
        private final int maxRadius;
        private double radius;
        private double alpha;
        private double progress;
        private boolean finished;

        private static final double DURATION = 0.4;
        private static final double INITIAL_RADIUS = 3;
        private static final double INITIAL_ALPHA = 0.95;

        public ClickEffect(int x, int y) {
            this.x = x;
            this.y = y;
            this.maxRadius = 15;
            this.radius = INITIAL_RADIUS;
            this.alpha = INITIAL_ALPHA;
            this.progress = 0;
            this.finished = false;
        }

        public void update(double deltaProgress) {
            if (finished) return;

            progress += deltaProgress;
            if (progress >= 1.0) {
                progress = 1.0;
                finished = true;
            }

            double eased = easeOutQuad(progress);
            radius = INITIAL_RADIUS + (maxRadius - INITIAL_RADIUS) * eased;
            alpha = INITIAL_ALPHA * (1.0 - eased);
        }

        private double easeOutQuad(double t) {
            return t * (2 - t);
        }

        public boolean isFinished() {
            return finished;
        }

        public void draw(Graphics2D g2) {
            if (finished || alpha <= 0) return;

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
            g2.setColor(new Color(255, 80, 80, (int) (alpha * 255)));
            g2.fillOval(x - (int) radius, y - (int) radius, (int) (radius * 2), (int) (radius * 2));

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 80, 80, (int) (alpha * 255)));
            g2.drawOval(x - (int) radius, y - (int) radius, (int) (radius * 2), (int) (radius * 2));

            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    private void addClickEffect(Point screenPoint) {
        ClickEffect effect = new ClickEffect(screenPoint.x, screenPoint.y);

        synchronized (clickEffects) {
            while (clickEffects.size() >= 10) {
                clickEffects.remove(0);
            }
            clickEffects.add(effect);
        }
    }

    public void reset() {
        sizeInitialized = false;
        currentWidth = 0;
        currentHeight = 0;
        frame = null;
        synchronized (clickEffects) {
            clickEffects.clear();
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        requestFocusInWindow();
    }

    private Cursor createTransparentCursor() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        return Toolkit.getDefaultToolkit().createCustomCursor(img, new Point(0, 0), "hidden");
    }

}
