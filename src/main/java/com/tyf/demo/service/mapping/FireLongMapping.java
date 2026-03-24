package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

/**
 * @desc : 鼠标左键长按 → 开火（按住连发）
 * @auth : tyf
 * @date : 2026-03-20
 */
public class FireLongMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.FIRE_LONG;
    }

    @Override
    public String getDisplayName() {
        return "开火(长按)";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.CLICK;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.CLICK);
        e.setTriggerType(TriggerType.MOUSE_LEFT);
        e.setMousePressMode(MousePressMode.LONG_PRESS);
        e.setPhoneX(0.88f);
        e.setPhoneY(0.62f);
        e.setEnabled(true);
    }
}
