package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : 静步（按住生效）
 * @auth : tyf
 * @date : 2026-03-20
 */
public class WalkSlowMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.WALK_SLOW;
    }

    @Override
    public String getDisplayName() {
        return "静步";
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
        e.setKeyCode(KeyEvent.VK_ALT);
        e.setKeyName("ALT");
        e.setKeyboardPressMode(com.tyf.demo.service.GameMappingConfig.KeyboardPressMode.HOLD);
        e.setPhoneX(0.34f);
        e.setPhoneY(0.78f);
        e.setEnabled(true);
    }
}
