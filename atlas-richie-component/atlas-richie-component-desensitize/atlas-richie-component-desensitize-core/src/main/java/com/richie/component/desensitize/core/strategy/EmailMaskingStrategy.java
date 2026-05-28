package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 邮箱脱敏：本地部分保留首字符。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class EmailMaskingStrategy implements MaskingStrategy {

    /**
     * 判断是否支持邮箱脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.EMAIL;
    }

    /**
     * 脱敏邮箱本地部分，域名保持不变。
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
        int at = raw.indexOf('@');
        if (at <= 0) {
            return String.valueOf(rule.maskChar()).repeat(Math.min(raw.length(), 3));
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        if (local.length() == 1) {
            return local + "***" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
