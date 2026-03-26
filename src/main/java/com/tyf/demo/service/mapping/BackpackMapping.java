package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : 打开背包：键盘 + 屏幕背包按钮
 * @auth : tyf
 * @date : 2026-03-20
 */
public class BackpackMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.BACKPACK;
    }

    @Override
    public String getDisplayName() {
        return "打开背包";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.CLICK;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.CLICK);
        e.setTriggerType(TriggerType.KEYBOARD);
        e.setMousePressMode(MousePressMode.NONE);
        e.setKeyCode(KeyEvent.VK_CONTROL);
        e.setKeyName("CTRL");
        e.setPhoneX(0.06f);
        e.setPhoneY(0.28f);
        e.setEnabled(true);
    }
}
