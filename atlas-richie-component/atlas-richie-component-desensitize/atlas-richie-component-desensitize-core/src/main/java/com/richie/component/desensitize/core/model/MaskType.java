package com.richie.component.desensitize.core.model;

/**
 * 脱敏遮罩类型
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public enum MaskType {
    /**
     * 电话号码
     */
    PHONE,
    /**
     * 身份证号
     */
    ID_CARD,
    /**
     * 电子邮件
     */
    EMAIL,
    /**
     * 银行卡号
     */
    BANK_CARD,
    /**
     * 姓名
     */
    NAME,
    /**
     * 地址
     */
    ADDRESS,
    /**
     * 密码
     */
    PASSWORD,
    /**
     * 自定义
     */
    CUSTOM
}
