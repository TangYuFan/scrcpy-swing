package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

/**
 * @desc : 鼠标右键 → 开镜
 * @auth : tyf
 * @date : 2026-03-20
 */
public class AimMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.AIM;
    }

    @Override
    public String getDisplayName() {
        return "开镜";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.CLICK;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.CLICK);
        e.setTriggerType(TriggerType.MOUSE_RIGHT);
        e.setMousePressMode(MousePressMode.NONE);
        e.setPhoneX(0.88f);
        e.setPhoneY(0.45f);
        e.setEnabled(true);
    }
}
