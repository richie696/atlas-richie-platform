/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.stereotype.Component;

/**
 * 银行卡脱敏：保留前 4 后 4，中间以空格分组掩码。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class BankCardMaskingStrategy implements MaskingStrategy {

    /**
     * 判断是否支持银行卡脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.BANK_CARD;
    }

    /**
     * 保留前后卡号，中间使用分组掩码。
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
        String digits = raw.replaceAll("\\s+", "");
        if (digits.length() <= 8) {
            return String.valueOf(rule.maskChar()).repeat(raw.length());
        }
        String prefix = digits.substring(0, 4);
        String suffix = digits.substring(digits.length() - 4);
        return prefix + " **** **** " + suffix;
    }
}
