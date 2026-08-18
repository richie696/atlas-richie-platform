/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.desensitize.core.strategy;

import cn.richie696.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * API 凭证脱敏：保留首尾，默认用固定 4 个掩码字符替换中间内容。
 */
@Component
public class ApiKeyMaskingStrategy extends AbstractKeepEdgeMaskingStrategy {

    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.API_KEY;
    }

    @Override
    protected MaskType supportedType() {
        return MaskType.API_KEY;
    }
}
