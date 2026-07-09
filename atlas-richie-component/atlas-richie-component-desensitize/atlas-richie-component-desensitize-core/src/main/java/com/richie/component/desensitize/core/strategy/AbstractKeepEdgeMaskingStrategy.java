/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskType;
public abstract class AbstractKeepEdgeMaskingStrategy implements MaskingStrategy {

    /**
     * 按规则保留两端字符并掩码中间内容。
     *
     * @param raw 原始字符串
     * @param rule 脱敏规则
     * @return 脱敏结果
     */
    @Override
    public String mask(String raw, MaskRule rule) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        int left = rule.keepLeft();
        int right = rule.keepRight();
        char maskChar = rule.maskChar();
        if (left + right >= raw.length()) {
            return String.valueOf(maskChar).repeat(raw.length());
        }
        String prefix = raw.substring(0, left);
        String suffix = raw.substring(raw.length() - right);
        int maskLen = raw.length() - left - right;
        return prefix + String.valueOf(maskChar).repeat(maskLen) + suffix;
    }

    protected abstract MaskType supportedType();
}
