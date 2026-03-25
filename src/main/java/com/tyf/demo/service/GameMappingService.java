package com.tyf.demo.service;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.TriggerType;
import com.tyf.demo.service.mapping.BuiltinMappingIds;
import org.pmw.tinylog.Logger;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameMappingService {

    /** 视角滑动专用 pointerId，重新实现视角移动时可沿用 */
    public static final long PTR_MOUSE_VIEW = 1002L;
    private static final long PTR_JOYSTICK = 1001L;
    private static final long PTR_LEFT_LONG = 1003L;
    private static final long PTR_RIGHT_AIM = 1004L;
    private static final long PTR_CLICK_BASE = 2000L;
    private static final long CLICK_UP_DELAY_MS = 35L;
    // 模拟“摇杆推得越远速度越快”：默认中等推杆，奔跑键=大推杆，静步键=小推杆
    private static final float JOYSTICK_NORMAL_SCALE = 0.78f;
    private static final float JOYSTICK_RUN_SCALE = 1.00f;
    private static final float JOYSTICK_WALK_SLOW_SCALE = 0.45f;
    private static final float JOYSTICK_RADIUS_MIN = 0.03f;
    private static final float JOYSTICK_RADIUS_MAX = 0.35f;

    private static int currentVideoWidth = 1080;
    private static int currentVideoHeight = 1920;

    private static final Set<Integer> nonRepeatKeysDown = new HashSet<>();
    private static final Map<Integer, MappingEntry> keyboardHoldActive = new HashMap<>();

    private static final boolean[] wasdDown = new boolean[4];
    /** 仅允许：单键 W/A/S/D 与斜向 WA、WD、AS、SD；由 collapseWasdToEight() 维护 */
    private enum WasdChord {
        NONE,
        W,
        A,
        S,
        D,
        WA,
        WD,
        AS,
        SD
    }

    private static WasdChord wasdChord = WasdChord.NONE;
    /** 竖直轴 W/S 最后按下：0=W，2=S，-1=无 */
    private static int lastWasdVertical = -1;
    /** 水平轴 A/D 最后按下：1=A，3=D，-1=无 */
    private static int lastWasdHorizontal = -1;
    /** 最后一次按下的 WASD 键索引 0..3（用于无法归类时退化为单键） */
    private static int lastPressedWasdIdx = -1;
    private static volatile boolean runModifierDown;
    private static volatile boolean walkSlowModifierDown;

    private static boolean joystickTouchActive;
    private static int joystickCenterX;
    private static int joystickCenterY;
    private static int lastJoystickX;
    private static int lastJoystickY;

    private static volatile boolean leftButtonHoldActive;
    private static MappingEntry activeLeftButtonEntry;
    private static ScheduledExecutorService actionScheduler;

    // ---------- 视角（鼠标移动 -> 单指拖拽）----------
    private static final float VIEW_NORM_MIN = 0.05f;
    private static final float VIEW_NORM_MAX = 0.95f;
    private static final long MOUSE_VIEW_TIMEOUT_MS = 500L;
    private static final float VIEW_MOVE_DEADZONE_PX = 0.35f;
    private static final long MOUSE_VIEW_IGNORE_STEP_MS = 12L;
    private static volatile boolean mouseViewTouching;
    private static float mouseViewNormX = 0.5f;
    private static float mouseViewNormY = 0.5f;
    private static volatile long ignoreMouseMoveUntilMs;
    private static ScheduledExecutorService mouseViewScheduler;
    private static ScheduledFuture<?> mouseViewTimeoutFuture;

    public enum RealtimeProfile {
        STABLE,
        BALANCED,
        RESPONSIVE
    }

    private GameMappingService() {}

    static {
        applyRealtimeProfile(RealtimeProfile.BALANCED);
    }

    /**
     * 预留：实时档位（原用于视角移动等参数，已清空，可在重新实现时接入）。
     */
    public static void applyRealtimeProfile(RealtimeProfile profile) {
        // no-op
    }

    public static void resetState() {
        nonRepeatKeysDown.clear();
        releaseAllKeyboardHoldMappings();
        for (int i = 0; i < wasdDown.length; i++) {
            wasdDown[i] = false;
        }
        lastWasdVertical = -1;
        lastWasdHorizontal = -1;
        lastPressedWasdIdx = -1;
        wasdChord = WasdChord.NONE;
        runModifierDown = false;
        walkSlowModifierDown = false;
        if (joystickTouchActive) {
            ControlService.sendTouchUp(PTR_JOYSTICK, lastJoystickX, lastJoystickY);
            joystickTouchActive = false;
        }
        joystickCenterX = 0;
        joystickCenterY = 0;
        leftButtonHoldActive = false;
        activeLeftButtonEntry = null;
        mouseViewStopTouchInternal();
        ignoreMouseMoveUntilMs = 0L;

        // Logger.debug("game mapping: state reset");
    }

    public static void updateVideoSize(int width, int height) {
        currentVideoWidth = width;
        currentVideoHeight = height;
    }

    public static void handleKeyPressed(int keyCode) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        int wasdIdx = wasdIndex(keyCode);
        if (wasdIdx >= 0) {
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                // 不可在「已按下」时直接 return：系统会对长按重复触发 keyPressed，
                // 许多游戏需要持续的 touchMove 才能保持移动。
                wasdDown[wasdIdx] = true;
                lastPressedWasdIdx = wasdIdx;
                if (wasdIdx == 0 || wasdIdx == 2) {
                    lastWasdVertical = wasdIdx;
                } else {
                    lastWasdHorizontal = wasdIdx;
                }
                updateJoystickTouch(joy);
                return;
            }
        }

        MappingEntry runMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.RUN);
        if (runMapping != null && runMapping.getTriggerType() == TriggerType.KEYBOARD && runMapping.getKeyCode() == keyCode) {
            runModifierDown = true;
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                updateJoystickTouch(joy);
            }
            return;
        }

        MappingEntry walkSlowMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.WALK_SLOW);
        if (walkSlowMapping != null
                && walkSlowMapping.getTriggerType() == TriggerType.KEYBOARD
                && walkSlowMapping.getKeyCode() == keyCode) {
            walkSlowModifierDown = true;
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                updateJoystickTouch(joy);
            }
            return;
        }

        MappingEntry entry = GameMappingConfig.findMappingByKeyCode(keyCode);
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        if (entry.getTriggerType() == TriggerType.KEYBOARD
                && entry.getType() == MappingType.CLICK
                && entry.getKeyboardPressMode() == GameMappingConfig.KeyboardPressMode.HOLD) {
            // 参考 QtScrcpy：按下即 down，松开即 up；若此前 release 丢失，自动纠正状态
            if (keyboardHoldActive.containsKey(keyCode)) {
                return;
            }
            long pointerId = pointerIdForKeyboardHold(entry);
            int x = clampToScreenX(entry.getPhoneX());
            int y = clampToScreenY(entry.getPhoneY());
            ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            keyboardHoldActive.put(keyCode, entry);
            nonRepeatKeysDown.add(keyCode);
            // Logger.info("game mapping: triggered - " + entry.getName() + " (键盘长按开始)");
            return;
        }

        if (nonRepeatKeysDown.contains(keyCode)) {
            return;
        }
        nonRepeatKeysDown.add(keyCode);

        executeMapping(entry);
    }

    public static void handleKeyReleased(int keyCode) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        int wasdIdx = wasdIndex(keyCode);
        if (wasdIdx >= 0) {
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                if (!wasdDown[wasdIdx]) {
                    return;
                }
                wasdDown[wasdIdx] = false;
                if (wasdIdx == 0 || wasdIdx == 2) {
                    if (lastWasdVertical == wasdIdx) {
                        lastWasdVertical = wasdDown[0] ? 0 : (wasdDown[2] ? 2 : -1);
                    }
                } else {
                    if (lastWasdHorizontal == wasdIdx) {
                        lastWasdHorizontal = wasdDown[1] ? 1 : (wasdDown[3] ? 3 : -1);
                    }
                }
                updateJoystickTouch(joy);
                return;
            }
        }

        nonRepeatKeysDown.remove(keyCode);
        MappingEntry hold = keyboardHoldActive.remove(keyCode);
        if (hold != null) {
            long pointerId = pointerIdForKeyboardHold(hold);
            int x = clampToScreenX(hold.getPhoneX());
            int y = clampToScreenY(hold.getPhoneY());
            ControlService.sendTouchUp(pointerId, x, y);
            // Logger.info("game mapping: triggered - " + hold.getName() + " (键盘长按结束)");
        }

        boolean changed = false;
        MappingEntry runMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.RUN);
        if (runMapping != null && runMapping.getTriggerType() == TriggerType.KEYBOARD && runMapping.getKeyCode() == keyCode) {
            runModifierDown = false;
            changed = true;
        }
        MappingEntry walkSlowMapping = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.WALK_SLOW);
        if (walkSlowMapping != null
                && walkSlowMapping.getTriggerType() == TriggerType.KEYBOARD
                && walkSlowMapping.getKeyCode() == keyCode) {
            walkSlowModifierDown = false;
            changed = true;
        }
        if (changed) {
            MappingEntry joy = GameMappingConfig.getJoystickMapping();
            if (joy != null && joy.isEnabled()) {
                updateJoystickTouch(joy);
            }
        }
    }

    private static int wasdIndex(int keyCode) {
        if (keyCode == KeyEvent.VK_W) {
            return 0;
        }
        if (keyCode == KeyEvent.VK_A) {
            return 1;
        }
        if (keyCode == KeyEvent.VK_S) {
            return 2;
        }
        if (keyCode == KeyEvent.VK_D) {
            return 3;
        }
        return -1;
    }

    /**
     * 同轴对向只保留「最后按下」一侧，再映射到 8 种合法和弦之一并写回 wasdDown。
     */
    private static void collapseWasdToEight() {
        if (wasdDown[0] && wasdDown[2]) {
            if (lastWasdVertical == 2) {
                wasdDown[0] = false;
            } else {
                wasdDown[2] = false;
            }
        }
        if (wasdDown[1] && wasdDown[3]) {
            if (lastWasdHorizontal == 3) {
                wasdDown[1] = false;
            } else {
                wasdDown[3] = false;
            }
        }
        boolean w = wasdDown[0];
        boolean a = wasdDown[1];
        boolean s = wasdDown[2];
        boolean d = wasdDown[3];
        WasdChord chord = chordFromPhysicalKeys(w, a, s, d);
        if (chord == null) {
            chord = chordFallbackSingle(lastPressedWasdIdx);
        }
        applyWasdChord(chord);
    }

    private static WasdChord chordFromPhysicalKeys(boolean w, boolean a, boolean s, boolean d) {
        int n = (w ? 1 : 0) + (a ? 1 : 0) + (s ? 1 : 0) + (d ? 1 : 0);
        if (n == 0) {
            return WasdChord.NONE;
        }
        if (n == 1) {
            if (w) {
                return WasdChord.W;
            }
            if (a) {
                return WasdChord.A;
            }
            if (s) {
                return WasdChord.S;
            }
            return WasdChord.D;
        }
        if (n == 2) {
            if (w && a && !s && !d) {
                return WasdChord.WA;
            }
            if (w && d && !a && !s) {
                return WasdChord.WD;
            }
            if (a && s && !w && !d) {
                return WasdChord.AS;
            }
            if (s && d && !w && !a) {
                return WasdChord.SD;
            }
            return null;
        }
        return null;
    }

    private static WasdChord chordFallbackSingle(int idx) {
        if (idx == 0) {
            return WasdChord.W;
        }
        if (idx == 1) {
            return WasdChord.A;
        }
        if (idx == 2) {
            return WasdChord.S;
        }
        if (idx == 3) {
            return WasdChord.D;
        }
        return WasdChord.NONE;
    }

    private static void applyWasdChord(WasdChord c) {
        wasdChord = c;
        for (int i = 0; i < 4; i++) {
            wasdDown[i] = false;
        }
        switch (c) {
            case NONE:
                break;
            case W:
                wasdDown[0] = true;
                break;
            case A:
                wasdDown[1] = true;
                break;
            case S:
                wasdDown[2] = true;
                break;
            case D:
                wasdDown[3] = true;
                break;
            case WA:
                wasdDown[0] = true;
                wasdDown[1] = true;
                break;
            case WD:
                wasdDown[0] = true;
                wasdDown[3] = true;
                break;
            case AS:
                wasdDown[1] = true;
                wasdDown[2] = true;
                break;
            case SD:
                wasdDown[2] = true;
                wasdDown[3] = true;
                break;
            default:
                break;
        }
    }


    private static void chordToDirection(WasdChord chord, float[] outDxDy) {
        float dx = 0f;
        float dy = 0f;
        switch (chord) {
            case NONE:
                break;
            case W:
                dy = -1f;
                break;
            case A:
                dx = -1f;
                break;
            case S:
                dy = 1f;
                break;
            case D:
                dx = 1f;
                break;
            case WA:
                dx = -0.70710678f;
                dy = -0.70710678f;
                break;
            case WD:
                dx = 0.70710678f;
                dy = -0.70710678f;
                break;
            case AS:
                dx = -0.70710678f;
                dy = 0.70710678f;
                break;
            case SD:
                dx = 0.70710678f;
                dy = 0.70710678f;
                break;
            default:
                break;
        }
        outDxDy[0] = dx;
        outDxDy[1] = dy;
    }

    private static void updateJoystickTouch(MappingEntry entry) {
        collapseWasdToEight();
        float[] dir = new float[2];
        chordToDirection(wasdChord, dir);
        float dx = dir[0];
        float dy = dir[1];
        boolean any = dx != 0f || dy != 0f;

        int sw = Math.max(1, currentVideoWidth);
        int sh = Math.max(1, currentVideoHeight);
        float cx = entry.getPhoneX();
        float cy = entry.getPhoneY();
        float r = resolveJoystickRadius(entry);

        if (!any) {
            if (joystickTouchActive) {
                ControlService.sendTouchUp(PTR_JOYSTICK, lastJoystickX, lastJoystickY);
                joystickTouchActive = false;
            }
            return;
        }

        // 在归一化坐标 (0~1) 下计算终点，再映射到像素，避免 (int) 截断中心点 + minSide 混用导致的方位偏差
        float nx = cx + dx * r;
        float ny = cy + dy * r;
        nx = Math.max(0f, Math.min(1f, nx));
        ny = Math.max(0f, Math.min(1f, ny));

        int px = (int) Math.round(nx * (sw - 1));
        int py = (int) Math.round(ny * (sh - 1));
        px = Math.max(0, Math.min(px, sw - 1));
        py = Math.max(0, Math.min(py, sh - 1));

        int centerX = (int) Math.round(cx * (sw - 1));
        int centerY = (int) Math.round(cy * (sh - 1));
        centerX = Math.max(0, Math.min(centerX, sw - 1));
        centerY = Math.max(0, Math.min(centerY, sh - 1));

        Logger.info("[joystick] chord={} video={}x{} normCenter=({},{}) pxCenter=({},{}) normTouch=({},{}) pxTouch=({},{}) dx={} dy={} r={}",
                wasdChord,
                sw, sh,
                cx, cy,
                centerX, centerY,
                nx, ny,
                px, py,
                dx, dy, r);

        if (!joystickTouchActive) {
            // 先按住摇杆中心，再拖拽到目标点，确保被游戏识别为“拖动摇杆”
            joystickCenterX = centerX;
            joystickCenterY = centerY;
            ControlService.sendTouchDown(PTR_JOYSTICK, joystickCenterX, joystickCenterY, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
            if (px != joystickCenterX || py != joystickCenterY) {
                ControlService.sendTouchMove(PTR_JOYSTICK, px, py);
            }
            // DOWN 已在通道内立即 flush；首条 MOVE 仍可能留在缓冲，再刷一次避免端上先处理错误方向的 MOVE
            ControlService.flushTouchOutput();
            joystickTouchActive = true;
            lastJoystickX = px;
            lastJoystickY = py;
        } else {
            ControlService.sendTouchMove(PTR_JOYSTICK, px, py);
            lastJoystickX = px;
            lastJoystickY = py;
        }
    }

    /**
     * 游戏模式下鼠标相对位移：单指持续拖拽，映射视角（与摇杆不同 pointerId，可并行）。
     * 对齐 QtScrcpy：位移先除以 speedRatio，再按视频宽高归一化累加。
     *
     * @param deltaX 相对上一帧的像素位移
     * @param deltaY 相对上一帧的像素位移
     */
    public static void handleMouseMoved(int deltaX, int deltaY) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }
        MappingEntry entry = GameMappingConfig.getMouseMoveMapping();
        if (entry == null || !entry.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < ignoreMouseMoveUntilMs) {
            return;
        }

        int sw = Math.max(1, currentVideoWidth);
        int sh = Math.max(1, currentVideoHeight);
        int sens = Math.max(1, Math.min(10, entry.getMouseSensitivity()));
        float speedRatio = resolveViewSpeedRatio(sens);

        float rdx = deltaX;
        float rdy = deltaY;
        if (Math.abs(rdx) < VIEW_MOVE_DEADZONE_PX) {
            rdx = 0f;
        }
        if (Math.abs(rdy) < VIEW_MOVE_DEADZONE_PX) {
            rdy = 0f;
        }
        if (rdx == 0f && rdy == 0f) {
            return;
        }

        float dnx = (rdx / speedRatio) / (float) sw;
        float dny = (rdy / speedRatio) / (float) sh;

        synchronized (GameMappingService.class) {
            if (!mouseViewTouching) {
                mouseViewStartTouch(entry);
            }
            resetMouseViewTimeout();

            float nx = mouseViewNormX + dnx;
            float ny = mouseViewNormY + dny;

            if (nx < VIEW_NORM_MIN || nx > VIEW_NORM_MAX || ny < VIEW_NORM_MIN || ny > VIEW_NORM_MAX) {
                mouseViewStopTouchInternal();
                ignoreNextMouseMoves(4);
                return;
            }

            mouseViewNormX = nx;
            mouseViewNormY = ny;

            int px = clampInt((int) Math.round(mouseViewNormX * (sw - 1)), 0, sw - 1);
            int py = clampInt((int) Math.round(mouseViewNormY * (sh - 1)), 0, sh - 1);
            ControlService.sendTouchMove(PTR_MOUSE_VIEW, px, py);
        }
    }

    /**
     * 鼠标回绕等场景下忽略若干次位移，避免 delta 爆 spike。
     */
    public static void ignoreNextMouseMoves(int count) {
        if (count <= 0) {
            return;
        }
        long until = System.currentTimeMillis() + (long) count * MOUSE_VIEW_IGNORE_STEP_MS;
        ignoreMouseMoveUntilMs = Math.max(ignoreMouseMoveUntilMs, until);
    }

    private static float resolveViewSpeedRatio(int sensitivity) {
        // 数值越大越灵敏（同位移转得越快）：speedRatio 越小
        float minR = 8f;
        float maxR = 1f;
        return minR - (sensitivity - 1) * (minR - maxR) / 9f;
    }

    private static void mouseViewStartTouch(MappingEntry entry) {
        float sx = entry.getPhoneX();
        float sy = entry.getPhoneY();
        if (sx <= 0f && sy <= 0f) {
            sx = 0.5f;
            sy = 0.5f;
        }
        mouseViewNormX = Math.max(VIEW_NORM_MIN, Math.min(VIEW_NORM_MAX, sx));
        mouseViewNormY = Math.max(VIEW_NORM_MIN, Math.min(VIEW_NORM_MAX, sy));
        int sw = Math.max(1, currentVideoWidth);
        int sh = Math.max(1, currentVideoHeight);
        int px = clampInt((int) Math.round(mouseViewNormX * (sw - 1)), 0, sw - 1);
        int py = clampInt((int) Math.round(mouseViewNormY * (sh - 1)), 0, sh - 1);
        ControlService.sendTouchDown(PTR_MOUSE_VIEW, px, py, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        mouseViewTouching = true;
        ControlService.flushTouchOutput();
    }

    private static void mouseViewStopTouchInternal() {
        synchronized (GameMappingService.class) {
            if (!mouseViewTouching) {
                cancelMouseViewTimeout();
                return;
            }
            int sw = Math.max(1, currentVideoWidth);
            int sh = Math.max(1, currentVideoHeight);
            int px = clampInt((int) Math.round(mouseViewNormX * (sw - 1)), 0, sw - 1);
            int py = clampInt((int) Math.round(mouseViewNormY * (sh - 1)), 0, sh - 1);
            ControlService.sendTouchUp(PTR_MOUSE_VIEW, px, py);
            mouseViewTouching = false;
            cancelMouseViewTimeout();
            ControlService.flushTouchOutput();
        }
    }

    private static void ensureMouseViewScheduler() {
        if (mouseViewScheduler == null || mouseViewScheduler.isShutdown()) {
            mouseViewScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "game-mapping-mouse-view");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private static void resetMouseViewTimeout() {
        cancelMouseViewTimeout();
        ensureMouseViewScheduler();
        mouseViewTimeoutFuture = mouseViewScheduler.schedule(
                () -> mouseViewStopTouchInternal(), MOUSE_VIEW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void cancelMouseViewTimeout() {
        if (mouseViewTimeoutFuture != null && !mouseViewTimeoutFuture.isDone()) {
            mouseViewTimeoutFuture.cancel(false);
        }
        mouseViewTimeoutFuture = null;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void ensureActionScheduler() {
        if (actionScheduler == null || actionScheduler.isShutdown()) {
            actionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "game-mapping-action");
                t.setDaemon(true);
                return t;
            });
        }
    }

    public static void handleMousePressed(int button) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        if (button == MouseEvent.BUTTON3) {
            MappingEntry aim = GameMappingConfig.findMappingByMouseRight();
            if (aim != null && aim.isEnabled() && aim.getType() == MappingType.CLICK) {
                // Logger.info("game mapping: triggered - " + aim.getName());
                executeClickDown(PTR_RIGHT_AIM, aim.getPhoneX(), aim.getPhoneY());
            }
            return;
        }

        if (button != MouseEvent.BUTTON1) {
            return;
        }

        MappingEntry leftEntry = GameMappingConfig.findMappingByMouseLeft();
        if (leftEntry == null || !leftEntry.isEnabled()) {
            return;
        }
        activeLeftButtonEntry = leftEntry;
        leftButtonHoldActive = true;
        executeClickDown(PTR_LEFT_LONG, leftEntry.getPhoneX(), leftEntry.getPhoneY());
    }

    public static void handleMouseReleased(int button) {
        if (!GameMappingConfig.isMappingMode()) {
            return;
        }
        if (!ControlService.isConnected()) {
            return;
        }

        if (button == MouseEvent.BUTTON3) {
            MappingEntry aim = GameMappingConfig.findMappingByMouseRight();
            if (aim != null && aim.isEnabled() && aim.getType() == MappingType.CLICK) {
                int x = (int) (currentVideoWidth * aim.getPhoneX());
                int y = (int) (currentVideoHeight * aim.getPhoneY());
                // Logger.info("game mapping: triggered - " + aim.getName() + " (释放)");
                ControlService.sendTouchUp(PTR_RIGHT_AIM, x, y);
            }
            return;
        }

        if (button != MouseEvent.BUTTON1) {
            return;
        }

        if (leftButtonHoldActive && activeLeftButtonEntry != null) {
            int x = clampToScreenX(activeLeftButtonEntry.getPhoneX());
            int y = clampToScreenY(activeLeftButtonEntry.getPhoneY());
            ControlService.sendTouchUp(PTR_LEFT_LONG, x, y);
        }
        leftButtonHoldActive = false;
        activeLeftButtonEntry = null;
    }

    private static void executeClickDown(long pointerId, float ratioX, float ratioY) {
        int x = clampToScreenX(ratioX);
        int y = clampToScreenY(ratioY);
        ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        // Logger.debug("game mapping: touch down at (" + ratioX + ", " + ratioY + ")");
    }

    private static void executeMapping(MappingEntry entry) {
        if (entry == null) {
            return;
        }

        // Logger.info("game mapping: triggered - " + entry.getName());

        switch (entry.getType()) {
            case CLICK:
                if (entry.getTriggerType() == TriggerType.MOUSE_RIGHT) {
                    return;
                }
                executeClick(entry);
                break;
            case MOUSE_MOVE:
            case JOYSTICK_WASD:
                break;
            default:
                Logger.warn("game mapping: unsupported type " + entry.getType());
                break;
        }
    }

    private static void executeClick(MappingEntry entry) {
        float ratioX = entry.getPhoneX();
        float ratioY = entry.getPhoneY();
        int x = clampToScreenX(ratioX);
        int y = clampToScreenY(ratioY);

        long pointerId = pointerIdForClick(entry);
        ControlService.sendTouchDown(pointerId, x, y, ControlMessage.AMOTION_EVENT_BUTTON_PRIMARY);
        ensureActionScheduler();
        int finalX = x;
        int finalY = y;
        actionScheduler.schedule(() -> ControlService.sendTouchUp(pointerId, finalX, finalY), CLICK_UP_DELAY_MS, TimeUnit.MILLISECONDS);

        // Logger.debug("game mapping: click at (" + ratioX + ", " + ratioY + ")");
    }

    private static long pointerIdForClick(MappingEntry entry) {
        if (entry == null) {
            return PTR_CLICK_BASE;
        }
        String seed = entry.getBuiltinId();
        if (seed == null || seed.isEmpty()) {
            seed = entry.getId();
        }
        if (seed == null || seed.isEmpty()) {
            seed = entry.getName();
        }
        int h = seed != null ? Math.abs(seed.hashCode()) : 1;
        return PTR_CLICK_BASE + (h % 900);
    }

    private static float currentJoystickScale() {
        if (walkSlowModifierDown) {
            return JOYSTICK_WALK_SLOW_SCALE;
        }
        if (runModifierDown) {
            return JOYSTICK_RUN_SCALE;
        }
        return JOYSTICK_NORMAL_SCALE;
    }

    private static float resolveJoystickRadius(MappingEntry joystickEntry) {
        if (joystickEntry == null) {
            return clampRadius(0.12f * JOYSTICK_NORMAL_SCALE);
        }
        float base = joystickEntry.getJoystickRadius();
        if (base <= 0f) {
            base = 0.12f;
        }

        if (walkSlowModifierDown) {
            MappingEntry walk = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.WALK_SLOW);
            float custom = radiusByTarget(joystickEntry, walk);
            if (custom > 0f) {
                return clampRadius(custom);
            }
            return clampRadius(base * JOYSTICK_WALK_SLOW_SCALE);
        }
        if (runModifierDown) {
            MappingEntry run = GameMappingConfig.findBuiltinMapping(BuiltinMappingIds.RUN);
            float custom = radiusByTarget(joystickEntry, run);
            if (custom > 0f) {
                return clampRadius(custom);
            }
            return clampRadius(base * JOYSTICK_RUN_SCALE);
        }
        return clampRadius(base * JOYSTICK_NORMAL_SCALE);
    }

    private static float radiusByTarget(MappingEntry joystickEntry, MappingEntry targetEntry) {
        if (joystickEntry == null || targetEntry == null || !targetEntry.isEnabled()) {
            return -1f;
        }
        float dx = targetEntry.getPhoneX() - joystickEntry.getPhoneX();
        float dy = targetEntry.getPhoneY() - joystickEntry.getPhoneY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist > 0f ? dist : -1f;
    }

    private static float clampRadius(float r) {
        return Math.max(JOYSTICK_RADIUS_MIN, Math.min(JOYSTICK_RADIUS_MAX, r));
    }

    private static long pointerIdForKeyboardHold(MappingEntry entry) {
        return 3000L + (pointerIdForClick(entry) % 900L);
    }

    private static int clampToScreenX(float ratioX) {
        int x = (int) (currentVideoWidth * ratioX);
        return Math.max(0, Math.min(x, currentVideoWidth - 1));
    }

    private static int clampToScreenY(float ratioY) {
        int y = (int) (currentVideoHeight * ratioY);
        return Math.max(0, Math.min(y, currentVideoHeight - 1));
    }

    private static void releaseAllKeyboardHoldMappings() {
        for (MappingEntry hold : keyboardHoldActive.values()) {
            if (hold == null) {
                continue;
            }
            long pointerId = pointerIdForKeyboardHold(hold);
            ControlService.sendTouchUp(pointerId, clampToScreenX(hold.getPhoneX()), clampToScreenY(hold.getPhoneY()));
        }
        keyboardHoldActive.clear();
    }
}
