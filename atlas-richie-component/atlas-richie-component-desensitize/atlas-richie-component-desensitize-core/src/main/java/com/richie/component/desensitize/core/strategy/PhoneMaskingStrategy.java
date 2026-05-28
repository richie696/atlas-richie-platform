package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 手机号脱敏：保留前 3 后 4。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class PhoneMaskingStrategy extends AbstractKeepEdgeMaskingStrategy {

    /**
     * 判断是否支持手机号脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.PHONE;
    }

    /**
     * supportedType。
     * @return 处理结果
     */
    @Override
    protected MaskType supportedType() {
        return MaskType.PHONE;
    }
}
