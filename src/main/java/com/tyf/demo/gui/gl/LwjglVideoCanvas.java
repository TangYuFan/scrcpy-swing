package com.tyf.demo.gui.gl;

import com.tyf.demo.service.ConstService;
import org.pmw.tinylog.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.Timer;

public class LwjglVideoCanvas extends AWTGLCanvas {

    private static final long serialVersionUID = 1L;

    private int textureId;
    private ByteBuffer uploadBuffer;
    private int texW;
    private int texH;
    private boolean hasFrame;
    private volatile boolean renderFailed;
    private boolean initialized;
    private volatile boolean glErrorLoggedOnce;
    private volatile boolean sizeLoggedOnce;
    private int allocatedW;
    private int allocatedH;

    // ---------- 点击波纹（OpenGL 绘制）----------
    private static final long RIPPLE_DURATION_NS = 400_000_000L;
    private static final float RIPPLE_INITIAL_RADIUS = 3f;
    private static final float RIPPLE_MAX_RADIUS = 15f;
    private static final float RIPPLE_INITIAL_ALPHA = 0.95f;
    private final List<Ripple> ripples = new ArrayList<>();
    private Timer rippleTimer;

    public LwjglVideoCanvas() {
        super(createGlData());
        setFocusable(false);
        Logger.info("[DEBUG] LwjglVideoCanvas: created");
    }

    private static GLData createGlData() {
        GLData d = new GLData();
        d.doubleBuffer = true;
        // 这里强制请求 OpenGL 2.1（兼容固定管线：glMatrixMode/glBegin 等），避免拿到 Core Profile 后“无画面”
        d.majorVersion = 2;
        d.minorVersion = 1;
        return d;
    }

    public boolean isRenderHealthy() {
        return !renderFailed;
    }

    /**
     * @desc : 叠加点击波纹（坐标为 Canvas 本地坐标）
     * @auth : tyf
     * @date : 2026-03-25
     */
    public void addClickRipple(Point localPoint) {
        if (localPoint == null) {
            return;
        }
        int cw = Math.max(1, getWidth());
        int ch = Math.max(1, getHeight());
        float nx = Math.max(0f, Math.min(1f, localPoint.x / (float) cw));
        float ny = Math.max(0f, Math.min(1f, localPoint.y / (float) ch));

        synchronized (ripples) {
            if (ripples.size() >= 10) {
                ripples.remove(0);
            }
            ripples.add(new Ripple(nx, ny, System.nanoTime()));
        }
        ensureRippleTimer();
        repaint();
    }

    private void ensureRippleTimer() {
        if (rippleTimer == null) {
            rippleTimer = new Timer(16, e -> {
                if (renderFailed) {
                    rippleTimer.stop();
                    return;
                }
                boolean has;
                synchronized (ripples) {
                    has = !ripples.isEmpty();
                }
                if (!has) {
                    rippleTimer.stop();
                    return;
                }
                repaint();
            });
            rippleTimer.setRepeats(true);
        }
        if (!rippleTimer.isRunning()) {
            rippleTimer.start();
        }
    }

    public void submitFrame(byte[] packedBgr, int w, int h) {
        if (!isDisplayable() || renderFailed) {
            return;
        }
        int need = w * h * 3;
        if (packedBgr == null || w <= 0 || h <= 0 || packedBgr.length < need) {
            return;
        }
        if (uploadBuffer == null || uploadBuffer.capacity() < need) {
            uploadBuffer = ByteBuffer.allocateDirect(need).order(ByteOrder.nativeOrder());
        }
        uploadBuffer.clear();
        uploadBuffer.put(packedBgr, 0, need);
        uploadBuffer.flip();
        texW = w;
        texH = h;
        hasFrame = true;
        // 让 AWT 的重绘节奏驱动 render()，避免在非 paint 周期 swap 不生效
        repaint();
    }

    public void clearVideo() {
        hasFrame = false;
        // reset 阶段可能发生在组件尚未就绪/上下文未建立时，直接 render 有概率触发 native FATAL
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        if (renderFailed) {
            return;
        }
        try {
            render();
        } catch (Throwable t) {
            renderFailed = true;
            Logger.error(t, "LWJGL paint/render 失败");
        }
    }

    @Override
    public void initGL() {
        if (renderFailed) {
            return;
        }
        try {
            GL.createCapabilities();
            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            // 固定管线：确保纹理颜色直接替换（避免被其它状态调制导致“看起来全黑”）
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
            allocatedW = 0;
            allocatedH = 0;
            initialized = true;
        } catch (Throwable t) {
            Logger.error(t, "LWJGL initGL 失败");
            renderFailed = true;
            initialized = false;
        }
    }

    @Override
    public void paintGL() {
        if (renderFailed || !initialized) {
            swapBuffers();
            return;
        }
        
        // 背景颜色与原 Swing 渲染一致
        Color bg = ConstService.THEME_CONTENT_BG;
        GL11.glClearColor(bg.getRed() / 255f, bg.getGreen() / 255f, bg.getBlue() / 255f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        int fbW = Math.max(1, getFramebufferWidth());
        int fbH = Math.max(1, getFramebufferHeight());
        // 有些情况下 componentResized 未触发，framebufferWidth/Height 会一直是 0，导致 viewport 变成 1x1 看起来“黑屏”
        if (fbW <= 1 || fbH <= 1) {
            fbW = Math.max(fbW, Math.max(1, getWidth()));
            fbH = Math.max(fbH, Math.max(1, getHeight()));
            if (!sizeLoggedOnce) {
                sizeLoggedOnce = true;
            }
        } else if (!sizeLoggedOnce) {
            sizeLoggedOnce = true;
        }
        GL11.glViewport(0, 0, fbW, fbH);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, fbW, fbH, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        if (!hasFrame || uploadBuffer == null || texW <= 0 || texH <= 0) {
            swapBuffers();
            return;
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        // 先分配，再用 SubImage 更新，减少每帧重建纹理带来的兼容性问题
        if (allocatedW != texW || allocatedH != texH) {
            allocatedW = texW;
            allocatedH = texH;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, texW, texH, 0,
                    GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, texW, texH,
                GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, uploadBuffer);

        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR && !glErrorLoggedOnce) {
            glErrorLoggedOnce = true;
            Logger.error("LWJGL GL error after glTexImage2D: 0x" + Integer.toHexString(err)
                    + ", texW=" + texW + ", texH=" + texH + ", canvasW=" + getWidth()
                    + ", canvasH=" + getHeight());
        }

        double videoAspect = (double) texW / (double) texH;
        double viewAspect = (double) fbW / (double) fbH;
        double drawW;
        double drawH;
        if (videoAspect > viewAspect) {
            drawW = fbW;
            drawH = fbW / videoAspect;
        } else {
            drawH = fbH;
            drawW = fbH * videoAspect;
        }
        double x0 = (fbW - drawW) / 2.0;
        double y0 = (fbH - drawH) / 2.0;

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2d(x0, y0);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2d(x0 + drawW, y0);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2d(x0 + drawW, y0 + drawH);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2d(x0, y0 + drawH);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        drawRipples(fbW, fbH);

        swapBuffers();
    }

    private void drawRipples(int fbW, int fbH) {
        long now = System.nanoTime();
        List<Ripple> snapshot;
        synchronized (ripples) {
            if (ripples.isEmpty()) {
                return;
            }
            for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
                Ripple r = it.next();
                if (now - r.startNs >= RIPPLE_DURATION_NS) {
                    it.remove();
                }
            }
            if (ripples.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(ripples);
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (Ripple r : snapshot) {
            float t = (now - r.startNs) / (float) RIPPLE_DURATION_NS;
            t = Math.max(0f, Math.min(1f, t));
            float eased = easeOutQuad(t);

            float radius = RIPPLE_INITIAL_RADIUS + (RIPPLE_MAX_RADIUS - RIPPLE_INITIAL_RADIUS) * eased;
            float alpha = RIPPLE_INITIAL_ALPHA * (1f - eased);

            float x = r.nx * fbW;
            float y = r.ny * fbH;

            GL11.glColor4f(1f, 0.31f, 0.31f, alpha * 0.35f);
            drawCircleFan(x, y, radius, 28);

            GL11.glLineWidth(1.5f);
            GL11.glColor4f(1f, 0.31f, 0.31f, alpha);
            drawCircleLine(x, y, radius, 32);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void drawCircleFan(float cx, float cy, float r, int segments) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            double a = (Math.PI * 2.0) * (i / (double) segments);
            GL11.glVertex2f((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r));
        }
        GL11.glEnd();
    }

    private static void drawCircleLine(float cx, float cy, float r, int segments) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double a = (Math.PI * 2.0) * (i / (double) segments);
            GL11.glVertex2f((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r));
        }
        GL11.glEnd();
    }

    private static float easeOutQuad(float t) {
        return t * (2f - t);
    }

    private static final class Ripple {
        private final float nx;
        private final float ny;
        private final long startNs;

        private Ripple(float nx, float ny, long startNs) {
            this.nx = nx;
            this.ny = ny;
            this.startNs = startNs;
        }
    }

    @Override
    public void removeNotify() {
        if (textureId != 0) {
            try {
                runInContext(() -> {
                    GL11.glDeleteTextures(textureId);
                    textureId = 0;
                });
            } catch (Exception ex) {
                textureId = 0;
            }
        }
        super.removeNotify();
    }
}
