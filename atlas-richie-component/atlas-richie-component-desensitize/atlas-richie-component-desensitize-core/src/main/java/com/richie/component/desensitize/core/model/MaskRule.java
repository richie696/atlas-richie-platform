package com.richie.component.desensitize.core.model;

/**
 * 单条脱敏规则（可由配置或注解推导）。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public record MaskRule(
        MaskType type,
        int keepLeft,
        int keepRight,
        char maskChar,
        String customStrategy
) {

    /**
     * 根据类型与掩码字符创建默认规则。
     *
     * @param type 脱敏类型
     * @param maskChar 掩码字符
     * @return 默认规则
     */
    public static MaskRule of(MaskType type, char maskChar) {
        return new MaskRule(type, defaultKeepLeft(type), defaultKeepRight(type), maskChar, null);
    }

    /**
     * 获取指定类型的默认左侧保留位数。
     *
     * @param type 脱敏类型
     * @return 默认左侧保留位数
     */
    public static int defaultKeepLeft(MaskType type) {
        return switch (type) {
            case PHONE -> 3;
            case ID_CARD -> 6;
            case BANK_CARD -> 4;
            case NAME -> 1;
            case ADDRESS -> 6;
            case EMAIL, PASSWORD, CUSTOM -> 0;
        };
    }

    /**
     * 获取指定类型的默认右侧保留位数。
     *
     * @param type 脱敏类型
     * @return 默认右侧保留位数
     */
    public static int defaultKeepRight(MaskType type) {
        return switch (type) {
            case PHONE, BANK_CARD -> 4;
            case ID_CARD -> 4;
            case NAME, ADDRESS, EMAIL, PASSWORD, CUSTOM -> 0;
        };
    }
}
