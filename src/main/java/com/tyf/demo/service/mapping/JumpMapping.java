package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : 跳跃：键盘 + 屏幕跳跃按钮
 * @auth : tyf
 * @date : 2026-03-20
 */
public class JumpMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.JUMP;
    }

    @Override
    public String getDisplayName() {
        return "跳跃";
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
        e.setKeyCode(KeyEvent.VK_SPACE);
        e.setKeyName("SPACE");
        e.setPhoneX(0.5f);
        e.setPhoneY(0.72f);
        e.setEnabled(true);
    }
}
