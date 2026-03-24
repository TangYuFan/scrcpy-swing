package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

/**
 * @desc : 鼠标左键点按 → 开火
 * @auth : tyf
 * @date : 2026-03-20
 */
public class FireTapMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.FIRE_TAP;
    }

    @Override
    public String getDisplayName() {
        return "开火(点按)";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.CLICK;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.CLICK);
        e.setTriggerType(TriggerType.MOUSE_LEFT);
        e.setMousePressMode(MousePressMode.TAP);
        e.setPhoneX(0.88f);
        e.setPhoneY(0.62f);
        e.setEnabled(true);
    }
}
