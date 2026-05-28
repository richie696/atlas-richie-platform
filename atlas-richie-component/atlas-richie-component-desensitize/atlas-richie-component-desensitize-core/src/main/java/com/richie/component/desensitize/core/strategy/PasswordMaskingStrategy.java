package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 密码全掩码。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class PasswordMaskingStrategy implements MaskingStrategy {

    /**
     * 常量定义。
     */
    private static final int DEFAULT_MASK_LENGTH = 6;

    /**
     * 判断是否支持密码脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.PASSWORD;
    }

    /**
     * 对密码进行全掩码，最短输出长度为 6。
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
        int len = Math.max(raw.length(), DEFAULT_MASK_LENGTH);
        return String.valueOf(rule.maskChar()).repeat(len);
    }
}
