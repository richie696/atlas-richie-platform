package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 姓名脱敏：保留首字符。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class NameMaskingStrategy implements MaskingStrategy {

    /**
     * 判断是否支持姓名脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.NAME;
    }

    /**
     * 保留姓名首字符，其余替换为掩码字符。
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
        if (raw.length() == 1) {
            return String.valueOf(rule.maskChar());
        }
        return raw.charAt(0) + String.valueOf(rule.maskChar()).repeat(raw.length() - 1);
    }
}
