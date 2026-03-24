package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : 预留自定义键位 1
 * @auth : tyf
 * @date : 2026-03-20
 */
public class Custom1Mapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.CUSTOM_1;
    }

    @Override
    public String getDisplayName() {
        return "自定义·1";
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
        e.setKeyCode(KeyEvent.VK_F1);
        e.setKeyName("F1");
        e.setPhoneX(0.5f);
        e.setPhoneY(0.5f);
        e.setEnabled(false);
    }
}
