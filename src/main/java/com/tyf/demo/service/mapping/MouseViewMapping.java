package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;
import com.tyf.demo.service.GameMappingConfig.TriggerType;

/**
 * @desc : 鼠标移动 → 视角（灵敏度可配）
 * @auth : tyf
 * @date : 2026-03-20
 */
public class MouseViewMapping extends AbstractBuiltinMapping {

    @Override
    public String getId() {
        return BuiltinMappingIds.MOUSE_VIEW;
    }

    @Override
    public String getDisplayName() {
        return "视角转向";
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.MOUSE_MOVE;
    }

    @Override
    public void applyDefaults(MappingEntry e) {
        e.setType(MappingType.MOUSE_MOVE);
        e.setTriggerType(TriggerType.MOUSE_MOVE);
        e.setMousePressMode(com.tyf.demo.service.GameMappingConfig.MousePressMode.NONE);
        e.setMouseSensitivity(3);
        e.setEnabled(true);
    }
}
