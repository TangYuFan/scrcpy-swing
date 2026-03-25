package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.MousePressMode;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

/**
 * @desc : 鼠标左键开火（按下开火，抬起停止）
 * @auth : tyf
 * @date : 2026-03-20
 */
public class FireMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.FIRE;
    }

    @Override
    public String getDisplayName() {
        return "开火";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.CLICK;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.CLICK);
        e.setTriggerType(TriggerType.MOUSE_LEFT);
        e.setMousePressMode(MousePressMode.NONE);
        e.setPhoneX(0.88f);
        e.setPhoneY(0.62f);
        e.setEnabled(true);
    }
}
