package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskType;

/**
 * 脱敏策略 SPI。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface MaskingStrategy {

    /**
     * 判断是否支持指定类型。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    boolean supports(MaskType type);

    /**
     * 对原始字符串执行脱敏。
     *
     * @param raw 原始字符串
     * @param rule 脱敏规则
     * @return 脱敏结果
     */
    String mask(String raw, MaskRule rule);
}
