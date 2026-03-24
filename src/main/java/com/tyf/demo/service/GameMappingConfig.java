package com.tyf.demo.service;

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
import java.util.List;
import java.util.UUID;

public class GameMappingConfig {

    public enum MappingType {
        CLICK("点击", "固定坐标点击"),
        DRAG("拖动", "PC鼠标拖动映射"),
        SWIPE("滑屏", "PC鼠标滑动手势"),
        MOUSE_MOVE("鼠标移动", "鼠标映射到屏幕");

        private final String desc;
        private final String help;

        MappingType(String desc, String help) {
            this.desc = desc;
            this.help = help;
        }

        public String getDesc() { return desc; }
        public String getHelp() { return help; }
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

        public String getDesc() { return desc; }
    }

    public static class MappingEntry {
        private String id;
        private String name;
        private boolean enabled;
        
        private MappingType type;
        private TriggerType triggerType;
        private int keyCode;
        private String keyName;
        
        private float phoneX;
        private float phoneY;
        private int mouseSensitivity;

        public MappingEntry() {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.name = "";
            this.enabled = true;
            this.type = MappingType.CLICK;
            this.triggerType = TriggerType.KEYBOARD;
            this.phoneX = 0.5f;
            this.phoneY = 0.5f;
            this.mouseSensitivity = 3;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public MappingType getType() { return type; }
        public void setType(MappingType type) { this.type = type; }
        public TriggerType getTriggerType() { return triggerType; }
        public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
        public int getKeyCode() { return keyCode; }
        public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
        public String getKeyName() { return keyName; }
        public void setKeyName(String keyName) { this.keyName = keyName; }
        public float getPhoneX() { return phoneX; }
        public void setPhoneX(float phoneX) { this.phoneX = phoneX; }
        public float getPhoneY() { return phoneY; }
        public void setPhoneY(float phoneY) { this.phoneY = phoneY; }
        public int getMouseSensitivity() { return mouseSensitivity; }
        public void setMouseSensitivity(int mouseSensitivity) { this.mouseSensitivity = mouseSensitivity; }

        public String getDisplayDesc() {
            switch (type) {
                case CLICK:
                    return String.format("点击(%.2f,%.2f)", phoneX, phoneY);
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
            switch (triggerType) {
                case KEYBOARD:
                    return keyName != null && !keyName.isEmpty() ? keyName : "未设置";
                case MOUSE_LEFT:
                    return "鼠标左键";
                case MOUSE_RIGHT:
                    return "鼠标右键";
                case MOUSE_MOVE:
                    return "鼠标移动";
                default:
                    return triggerType.getDesc();
            }
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
    }

    public static void toggleMappingMode() {
        mappingMode = !mappingMode;
        Logger.info("game mapping: mode toggled to " + (mappingMode ? "游戏映射" : "正常"));
    }

    public static List<MappingEntry> getMappings() {
        return mappings;
    }

    public static void addMapping(MappingEntry entry) {
        mappings.add(entry);
    }

    public static void removeMapping(String id) {
        mappings.removeIf(m -> m.getId().equals(id));
    }

    public static MappingEntry getMappingById(String id) {
        for (MappingEntry entry : mappings) {
            if (entry.getId().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMappingByKeyCode(int keyCode) {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) continue;
            if (entry.getTriggerType() == TriggerType.KEYBOARD && entry.getKeyCode() == keyCode) {
                return entry;
            }
        }
        return null;
    }

    public static MappingEntry findMappingByMouseButton(int button) {
        for (MappingEntry entry : mappings) {
            if (!entry.isEnabled()) continue;
            if (button == java.awt.event.MouseEvent.BUTTON1 && entry.getTriggerType() == TriggerType.MOUSE_LEFT) {
                return entry;
            }
            if (button == java.awt.event.MouseEvent.BUTTON3 && entry.getTriggerType() == TriggerType.MOUSE_RIGHT) {
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
            Type listType = new TypeToken<ArrayList<MappingEntry>>(){}.getType();
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
