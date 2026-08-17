package cn.richie696.gateway.error;

/**
 * 网关错误码条目值对象。
 * <p>
 * 用于在运行时传递错误码的完整元数据（错误码、HTTP 状态、是否可重试、
 * 文档 slug、i18n 键、协议版本）。所有字段均不可变。
 * </p>
 *
 * @author richie696
 * @since 2026-08-04
 */
public final class GatewayErrorEntry {

    /**
     * 稳定错误码字符串（如 {@code GW-AUTH-0001}）。
     */
    private final String errorCode;

    /**
     * HTTP 状态码。
     */
    private final int httpStatus;

    /**
     * 是否可重试。
     */
    private final boolean retryable;

    /**
     * 文档相对路径段（如 {@code gw-auth-token-invalid}）。
     */
    private final String docSlug;

    /**
     * i18n 键空间根（如 {@code error.auth.token.invalid}）。
     */
    private final String i18nKey;

    /**
     * 错误码协议版本。
     */
    private final String version;

    /**
     * 错误码详情页相对路径（如 {@code /gateway/errors/GW-AUTH-0001}）。
     */
    private final String helpUrl;

    /**
     * 构造错误码条目。
     *
     * @param errorCode  稳定错误码字符串
     * @param httpStatus HTTP 状态码
     * @param retryable  是否可重试
     * @param docSlug    文档 slug
     * @param i18nKey    i18n 键空间根
     * @param version    协议版本
     * @param helpUrl    详情页相对路径
     */
    public GatewayErrorEntry(String errorCode,
                             int httpStatus,
                             boolean retryable,
                             String docSlug,
                             String i18nKey,
                             String version,
                             String helpUrl) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.docSlug = docSlug;
        this.i18nKey = i18nKey;
        this.version = version;
        this.helpUrl = helpUrl;
    }

    /**
     * 返回稳定错误码字符串。
     *
     * @return 错误码字符串
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 返回对应的 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 是否可重试。
     *
     * @return true 表示可重试
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 返回文档相对路径段。
     *
     * @return slug
     */
    public String getDocSlug() {
        return docSlug;
    }

    /**
     * 返回 i18n 键空间根。
     *
     * @return i18nKey
     */
    public String getI18nKey() {
        return i18nKey;
    }

    /**
     * 返回错误码协议版本。
     *
     * @return 版本字符串
     */
    public String getVersion() {
        return version;
    }

    /**
     * 返回错误码详情页相对路径（如 {@code /gateway/errors/GW-AUTH-0001}）。
     *
     * @return helpUrl（始终为相对路径，不含 Host / Origin）
     */
    public String getHelpUrl() {
        return helpUrl;
    }
}
