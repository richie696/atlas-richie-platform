package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 身份证号脱敏：保留前 6 后 4。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class IdCardMaskingStrategy extends AbstractKeepEdgeMaskingStrategy {

    /**
     * 判断是否支持身份证号脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MaskType type) {
        return type == MaskType.ID_CARD;
    }

    /**
     * supportedType。
     * @return 处理结果
     */
    @Override
    protected MaskType supportedType() {
        return MaskType.ID_CARD;
    }
}
