package com.tyf.demo.service.mapping;

import com.tyf.demo.service.GameMappingConfig.MappingEntry;
import com.tyf.demo.service.GameMappingConfig.MappingType;

/**
 * @desc : 内置键位映射元数据与默认值（每个具体映射一个子类）
 * @auth : tyf
 * @date : 2026-03-20
 */
public abstract class AbstractBuiltinMapping {

    public abstract String getId();

    public abstract String getDisplayName();

    public String getHelpTitle() {
        return getMappingType().getDesc();
    }

    public String getHelpBody() {
        return getMappingType().getHelp();
    }

    public abstract MappingType getMappingType();

    /** 写入新建或合并条目时的默认值（坐标、灵敏度、键码等） */
    public abstract void applyDefaults(MappingEntry e);
}
