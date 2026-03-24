package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

import java.awt.event.KeyEvent;

/**
 * @desc : WASD → 左下角虚拟摇杆
 * @auth : tyf
 * @date : 2026-03-20
 */
public class WasdJoystickMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.WASD_JOYSTICK;
    }

    @Override
    public String getDisplayName() {
        return "WASD 虚拟摇杆";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.JOYSTICK_WASD;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.JOYSTICK_WASD);
        e.setTriggerType(TriggerType.KEYBOARD);
        e.setMousePressMode(com.tyf.demo.service.GameMappingConfig.MousePressMode.NONE);
        e.setKeyCode(KeyEvent.VK_UNDEFINED);
        e.setKeyName("");
        e.setPhoneX(0.22f);
        e.setPhoneY(0.78f);
        e.setJoystickRadius(0.14f);
        e.setEnabled(true);
    }
}
