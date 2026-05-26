package com.richie.component.web.exception;

/**
 * 平台 Web 层全局异常对应的错误码与国际化 key。
 *
 * @author richie696
 * @since 2022-10-09
 */
public enum EnumErrorMassage {

    /**
     * 请求参数无效异常
     */
    REQUEST_PARAMS_INVALID("00101001", "Request params is invalid", "platform.component.common.message.invalid.requestParams");

    /** HTTP 响应头中的状态码（如 400） */
    private final String statusCode;
    /** 默认错误文案 */
    private final String defaultMessage;
    /** 国际化资源 key */
    private final String i18nCode;

    private EnumErrorMassage(String statusCode, String defaultMessage, String i18nCode) {
        this.statusCode = statusCode;
        this.defaultMessage = defaultMessage;
        this.i18nCode = i18nCode;
    }

    /**
     * statusCode.
     *
     * @return the statusCode
     */
    public String getStatusCode() {
        return statusCode;
    }

    /**
     * defaultMessage.
     *
     * @return the defaultMessage
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * i18nCode.
     *
     * @return the i18nCode
     */
    public String getI18nCode() {
        return i18nCode;
    }

}
