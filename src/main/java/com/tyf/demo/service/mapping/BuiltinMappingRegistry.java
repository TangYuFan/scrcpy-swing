package com.tyf.demo.service.mapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @desc : 内置 FPS 映射顺序（与表格展示一致）
 * @auth : tyf
 * @date : 2026-03-20
 */
public final class BuiltinMappingRegistry {

    private static final List<AbstractBuiltinMapping> ORDERED = Collections.unmodifiableList(Arrays.asList(
            new WasdJoystickMapping(),
            new MouseViewMapping(),
            new FireMapping(),
            new AimMapping(),
            new JumpMapping(),
            new CrouchMapping(),
            new ProneMapping(),
            new ReloadMapping(),
            new BackpackMapping(),
            new Weapon1Mapping(),
            new Weapon2Mapping(),
            new Weapon3Mapping(),
            new PeekLeftMapping(),
            new PeekRightMapping(),
            new Custom1Mapping(),
            new Custom2Mapping(),
            new Custom3Mapping(),
            new Custom4Mapping(),
            new Custom5Mapping()
    ));

    private BuiltinMappingRegistry() {}

    public static List<AbstractBuiltinMapping> ordered() {
        return ORDERED;
    }

    public static AbstractBuiltinMapping byId(String id) {
        if (id == null) {
            return null;
        }
        for (AbstractBuiltinMapping m : ORDERED) {
            if (m.getId().equals(id)) {
                return m;
            }
        }
        return null;
    }
}
