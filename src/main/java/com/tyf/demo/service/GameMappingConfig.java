package com.tyf.demo.service;

import com.tyf.demo.gui.MainFrame;
import com.tyf.demo.service.mapping.AbstractBuiltinMapping;
import com.tyf.demo.service.mapping.BuiltinMappingRegistry;
import org.pmw.tinylog.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameMappingConfig {

    public enum MappingType {
        CLICK("点击", "在屏幕固定坐标执行一次点按"),
        DRAG("拖动", "PC 鼠标拖动映射"),
        SWIPE("滑屏", "PC 鼠标滑动手势"),
        MOUSE_MOVE("鼠标移动", "鼠标相对移动映射视角（灵敏度可配）"),
        JOYSTICK_WASD("WASD 虚拟摇杆", "W/A/S/D 控制左下角虚拟摇杆，需配置摇杆中心与半径");

        private final String desc;
        private final String help;

        MappingType(String desc, String help) {
            this.desc = desc;
            this.help = help;
        }

        public String getDesc() {
            return desc;
        }

        public String getHelp() {
            return help;
        }
    }

    public enum TriggerType {
        KEYBOARD("键盘"),
        MOUSE_LEFT("鼠标左键"),
        MOUSE_RIGHT("鼠标右键"),
        MOUSE_MOVE("鼠标移动");

        private final String desc;

        TriggerType(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /** 鼠标左键点按 / 长按区分（仅鼠标左键触发时有效） */
    public enum MousePressMode {
        NONE("普通"),
        TAP("点按"),
        LONG_PRESS("长按");

        private final String desc;

        MousePressMode(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /** 键盘触发时的按键行为：点按一次 or 按住生效松开取消 */
    public enum KeyboardPressMode {
        TAP("点按"),
        HOLD("长按");

        private final String desc;

        KeyboardPressMode(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    public static class MappingEntry {
        private String id;
        /** 内置映射稳定 ID，与 {@link com.tyf.demo.service.mapping.BuiltinMappingIds} 对应 */
        private String builtinId;
        private String name;
        private boolean enabled;

        private MappingType type;
        private TriggerType triggerType;
        private MousePressMode mousePressMode = MousePressMode.NONE;
        private KeyboardPressMode keyboardPressMode = KeyboardPressMode.TAP;

        private int keyCode;
        private String keyName;

        private float phoneX;
        private float phoneY;
        /** 虚拟摇杆最大偏移相对屏幕短边的比例，如 0.12 表示约 12% 屏宽 */
        private float joystickRadius;

        private int mouseSensitivity;

        public MappingEntry() {
            this.id = "";
            this.builtinId = "";
            this.name = "";
            this.enabled = true;
            this.type = MappingType.CLICK;
            this.triggerType = TriggerType.KEYBOARD;
            this.mousePressMode = MousePressMode.NONE;
            this.phoneX = 0.5f;
            this.phoneY = 0.5f;
            this.joystickRadius = 0.12f;
            this.mouseSensitivity = 3;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBuiltinId() {
            return builtinId;
        }

        public void setBuiltinId(String builtinId) {
            this.builtinId = builtinId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public MappingType getType() {
            return type;
        }

        public void setType(MappingType type) {
            this.type = type;
        }

        public TriggerType getTriggerType() {
            return triggerType;
        }

        public void setTriggerType(TriggerType triggerType) {
            this.triggerType = triggerType;
        }

        public MousePressMode getMousePressMode() {
            return mousePressMode != null ? mousePressMode : MousePressMode.NONE;
        }

        public void setMousePressMode(MousePressMode mousePressMode) {
            this.mousePressMode = mousePressMode != null ? mousePressMode : MousePressMode.NONE;
        }

        public KeyboardPressMode getKeyboardPressMode() {
            return keyboardPressMode != null ? keyboardPressMode : KeyboardPressMode.TAP;
        }

        public void setKeyboardPressMode(KeyboardPressMode keyboardPressMode) {
            this.keyboardPressMode = keyboardPressMode != null ? keyboardPressMode : KeyboardPressMode.TAP;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public void setKeyCode(int keyCode) {
            this.keyCode = keyCode;
        }

        public String getKeyName() {
            return keyName;
        }

        public void setKeyName(String keyName) {
            this.keyName = keyName;
        }

        public float getPhoneX() {
            return phoneX;
        }

        public void setPhoneX(float phoneX) {
            this.phoneX = phoneX;
        }

        public float getPhoneY() {
            return phoneY;
        }

        public void setPhoneY(float phoneY) {
            this.phoneY = phoneY;
        }

        public float getJoystickRadius() {
            return joystickRadius;
        }

        public void setJoystickRadius(float joystickRadius) {
            this.joystickRadius = joystickRadius;
        }

        public int getMouseSensitivity() {
            return mouseSensitivity;
        }

        public void setMouseSensitivity(int mouseSensitivity) {
            this.mouseSensitivity = mouseSensitivity;
        }

        public String getDisplayDesc() {
            switch (type) {
                case CLICK:
                    return String.format("屏幕(%.2f,%.2f)", phoneX, phoneY);
                case JOYSTICK_WASD:
                    return String.format("摇杆中心(%.2f,%.2f) 半径%.2f", phoneX, phoneY, joystickRadius);
                case DRAG:
                    return "拖动";
                case SWIPE:
                    return "滑屏";
                case MOUSE_MOVE:
                    return "灵敏度:" + mouseSensitivity;
                default:
                    return type.getDesc();
            }
        }

        public String getTriggerDesc() {
            if (type == MappingType.JOYSTICK_WASD) {
                return "W A S D";
            }
            if (triggerType == TriggerType.KEYBOARD) {
                String key = keyName != null && !keyName.isEmpty() ? keyName : "未设置";
                if (type == MappingType.CLICK) {
                    return key + "·" + getKeyboardPressMode().getDesc();
                }
                return key;
            }
            if (triggerType == TriggerType.MOUSE_LEFT) {
                if (mousePressMode == MousePressMode.TAP) {
                    return "鼠标左键·点按";
                }
                if (mousePressMode == MousePressMode.LONG_PRESS) {
                    return "鼠标左键·长按";
                }
                return "鼠标左键";
            }
            if (triggerType == TriggerType.MOUSE_RIGHT) {
                return "鼠标右键";
            }
            if (triggerType == TriggerType.MOUSE_MOVE) {
                return "鼠标移动";
            }
            return triggerType.getDesc();
        }
    }

    private static volatile boolean mappingMode = false;
    private static final List<MappingEntry> mappings = new ArrayList<>();
    private static final String MAPPING_CONFIG_FILE = ConstService.MAPPING_DIR + "config.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static boolean isMappingMode() {
        return mappingMode;
    }

    public static void setMappingMode(boolean mode) {
        Logger.info("game mapping: mode changed to " + (mode ? "游戏映射" : "正常"));
        
        mappingMode = mode;
        
        if (!mode) {
            GameMappingService.resetState();
        } else {
            java.awt.EventQueue.invokeLater(() -> {
                if (MainFrame.getMainFrame() != null && MainFrame.getMainFrame().getContentPanel() != null) {
                    MainFrame.getMainFrame().getContentPanel().requestFocusInWindow();
                }
            });
        }
    }

    public static void toggleMappingMode() {
        mappingMode = !mappingMode;
        Logger.info("game mapping: mode toggled to " + (mappingMode ? "游戏映射" : "正常"));
        
        if (!mappingMode) {
            GameMappingService.resetState();
        }
    }

    public static List<MappingEntry> getMappings() {
        return mappings;
    }

    /**
     * @desc : 加载文件后调用：合并/补齐内置映射，去除非内置条目
     */
    public static void ensureBuiltinMappings() {
        Map<String, MappingEntry> byBuiltin = new HashMap<>();
        for (MappingEntry m : mappings) {
            if (m.getBuiltinId() != null && !m.getBuiltinId().isEmpty()) {
                byBuiltin.put(m.getBuiltinId(), m);
            }
        }
        // 兼容旧版本：将「开火(点按/长按)」合并映射到新的「开火」
        if (!byBuiltin.containsKey(com.tyf.demo.service.mapping.BuiltinMappingIds.FIRE)) {
            MappingEntry legacy = byBuiltin.get(com.tyf.demo.service.mapping.BuiltinMappingIds.FIRE_LONG);
            if (legacy == null) {
                legacy = byBuiltin.get(com.tyf.demo.service.mapping.BuiltinMappingIds.FIRE_TAP);
            }
            if (legacy != null) {
                legacy.setBuiltinId(com.tyf.demo.service.mapping.BuiltinMappingIds.FIRE);
                legacy.setId(com.tyf.demo.service.mapping.BuiltinMappingIds.FIRE);
                legacy.setName("开火");
                legacy.setTriggerType(TriggerType.MOUSE_LEFT);
                legacy.setMousePressMode(MousePressMode.NONE);
                byBuiltin.put(com.tyf.demo.service.mapping.BuiltinMappingIds.FIRE, legacy);
            }
        }
        List<MappingEntry> next = new ArrayList<>();
        for (AbstractBuiltinMapping def : BuiltinMappingRegistry.ordered()) {
            MappingEntry e = byBuiltin.get(def.getId());
            if (e == null) {
                e = new MappingEntry();
                def.applyDefaults(e);
            }
            e.setBuiltinId(def.getId());
            e.setId(def.getId());
            if (e.getName() == null || e.getName().trim().isEmpty()) {
                e.setName(def.getDisplayName());
            }
            next.add(e);
        }
        mappings.clear();
        mappings.addAll(next);
    }

    public static MappingEntry getJoystickMapping() {
        for (MappingEntry entry : mappings) {
            if (entry.isEnabled() && entry.getType() == MappingType.JOYSTICK_WASD) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMappingByKeyCode(int keyCode) {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) {
                continue;
            }
            if (entry.getType() == MappingType.JOYSTICK_WASD) {
                continue;
            }
            if (entry.getTriggerType() == TriggerType.KEYBOARD && entry.getKeyCode() == keyCode) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMouseLeftTapMapping() {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) {
                continue;
            }
            if (entry.getTriggerType() == TriggerType.MOUSE_LEFT
                    && entry.getMousePressMode() == MousePressMode.TAP) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMouseLeftLongMapping() {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) {
                continue;
            }
            if (entry.getTriggerType() == TriggerType.MOUSE_LEFT
                    && entry.getMousePressMode() == MousePressMode.LONG_PRESS) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMappingByMouseLeft() {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) {
                continue;
            }
            if (entry.getType() == MappingType.CLICK && entry.getTriggerType() == TriggerType.MOUSE_LEFT) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMappingByMouseRight() {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) {
                continue;
            }
            if (entry.getTriggerType() == TriggerType.MOUSE_RIGHT) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry getMouseMoveMapping() {
        for (MappingEntry entry : mappings) {
            if (entry.isEnabled() && entry.getTriggerType() == TriggerType.MOUSE_MOVE) {
                return entry;
            }
        }
        return null;
    }

    public static void saveMappings() {
        try {
            File dir = new File(ConstService.MAPPING_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(MAPPING_CONFIG_FILE)) {
                gson.toJson(mappings, writer);
            }
            Logger.info("game mapping: saved to " + MAPPING_CONFIG_FILE);
        } catch (IOException e) {
            Logger.error("game mapping: save failed - " + e.getMessage());
        }
    }

    public static void loadMappings() {
        File configFile = new File(MAPPING_CONFIG_FILE);
        if (!configFile.exists()) {
            Logger.info("game mapping: no config file found");
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            Type listType = new TypeToken<ArrayList<MappingEntry>>() {
            }.getType();
            List<MappingEntry> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                mappings.clear();
                mappings.addAll(loaded);
                Logger.info("game mapping: loaded " + mappings.size() + " entries");
            }
        } catch (IOException e) {
            Logger.error("game mapping: load failed - " + e.getMessage());
        }
    }
}
