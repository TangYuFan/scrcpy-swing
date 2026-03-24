package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : 预留自定义键位 5
 * @auth : tyf
 * @date : 2026-03-20
 */
public class Custom5Mapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.CUSTOM_5;
    }

    @Override
    public String getDisplayName() {
        return "自定义·5";
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
        e.setKeyCode(KeyEvent.VK_F5);
        e.setKeyName("F5");
        e.setPhoneX(0.5f);
        e.setPhoneY(0.5f);
        e.setEnabled(false);
    }
}
