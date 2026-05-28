package com.richie.component.mfa.core.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * MFA 操作类型枚举
 * <p>
 * 用于表示 MFA 审计事件中的操作类型
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum MfaOperationTypeEnum {
    /**
     * 绑定 MFA 设备
     */
    BIND("BIND", "绑定设备"),

    /**
     * 解绑 MFA 设备
     */
    UNBIND("UNBIND", "解绑设备"),

    /**
     * 验证 MFA 码
     */
    VERIFY("VERIFY", "验证码验证"),

    /**
     * 激活 MFA 设备
     */
    ACTIVATE("ACTIVATE", "激活设备"),

    /**
     * 禁用 MFA 设备
     */
    DISABLE("DISABLE", "禁用设备");

    /**
     * 操作类型编码值（用于审计事件和日志）
     */
    private final String code;

    /**
     * 操作类型描述（用于显示和日志）
     */
    private final String desc;

    /**
     * 根据编码获取枚举
     *
     * @param code 操作类型编码
     * @return 枚举对象，如果不存在则返回null
     */
    public static MfaOperationTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据编码获取枚举（如果不存在则返回默认值）
     *
     * @param code 操作类型编码
     * @return 枚举对象，如果不存在则返回null
     */
    public static MfaOperationTypeEnum fromCodeOrDefault(String code) {
        return fromCode(code);
    }

    /**
     * 根据编码获取枚举（如果不存在则抛出异常）
     *
     * @param code 操作类型编码
     * @return 枚举对象
     * @throws IllegalArgumentException 如果编码不存在
     */
    public static MfaOperationTypeEnum fromCodeOrThrow(String code) {
        var enumValue = fromCode(code);
        if (enumValue == null) {
            throw new IllegalArgumentException("未知的MFA操作类型编码: " + code);
        }
        return enumValue;
    }

    /**
     * 判断编码是否存在
     *
     * @param code 操作类型编码
     * @return true-存在，false-不存在
     */
    public static boolean exists(String code) {
        return fromCode(code) != null;
    }
}
