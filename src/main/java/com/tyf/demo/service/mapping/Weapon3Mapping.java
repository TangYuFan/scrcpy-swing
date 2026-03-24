package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : 3 号武器
 * @auth : tyf
 * @date : 2026-03-20
 */
public class Weapon3Mapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.WEAPON_3;
    }

    @Override
    public String getDisplayName() {
        return "切枪·3";
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
        e.setKeyCode(KeyEvent.VK_3);
        e.setKeyName("3");
        e.setPhoneX(0.12f);
        e.setPhoneY(0.49f);
        e.setEnabled(true);
    }
}
