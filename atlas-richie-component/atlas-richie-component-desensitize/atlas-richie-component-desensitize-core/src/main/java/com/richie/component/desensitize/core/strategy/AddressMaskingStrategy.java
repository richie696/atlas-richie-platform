package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 地址脱敏：保留前 6 个字符。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class AddressMaskingStrategy extends AbstractKeepEdgeMaskingStrategy {

    @Override
    /**
     * 判断是否支持地址类型脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    public boolean supports(MaskType type) {
        return type == MaskType.ADDRESS;
    }

    /**
     * supportedType。
     * @return 处理结果
     */
    @Override
    protected MaskType supportedType() {
        return MaskType.ADDRESS;
    }
}
