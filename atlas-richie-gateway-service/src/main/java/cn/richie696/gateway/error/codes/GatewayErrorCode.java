package cn.richie696.gateway.error.codes;

/**
 * 网关错误码枚举。
 * <p>
 * 所有网关层抛出的稳定错误码统一使用 {@code GW-*} 命名空间，便于客户端按错误码
 * 做差异化处理（如 token 过期触发重新登录、限流触发退避重试）。每个错误码
 * 至少携带以下信息：
 * </p>
 * <ul>
 *   <li>{@code errorCode} — 稳定错误码字符串（如 {@code GW-AUTH-0001}）</li>
 *   <li>{@code httpStatus} — 对应的 HTTP 状态码</li>
 *   <li>{@code retryable} — 是否可重试（true 表示客户端可以重试）</li>
 *   <li>{@code docSlug} — 文档相对路径段（如 {@code gw-auth-token-invalid}）</li>
 *   <li>{@code i18nKey} — i18n 键空间根（如 {@code error.auth.token.invalid}）</li>
 *   <li>{@code version} — 错误码协议版本</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
public enum GatewayErrorCode {

    /**
     * 令牌非法（签名错、解码失败等）。
     */
    GW_AUTH_0001("GW-AUTH-0001", 401, false, "gw-auth-token-invalid", "error.auth.token.invalid", "4.6.0"),

    /**
     * 令牌缺失（请求未携带 Authorization 或 Authorization 格式错误）。
     */
    GW_AUTH_0002("GW-AUTH-0002", 401, false, "gw-auth-token-missing", "error.auth.token.missing", "4.6.0"),

    /**
     * 令牌过期（JWT exp 已过）。
     */
    GW_AUTH_0003("GW-AUTH-0003", 401, false, "gw-auth-token-expired", "error.auth.token.expired", "4.6.0"),

    /**
     * 权限不足（已认证但无权访问目标资源）。
     */
    GW_AUTH_0004("GW-AUTH-0004", 403, false, "gw-auth-permission-denied", "error.auth.permission.denied", "4.6.0"),

    /**
     * 租户标识缺失。
     */
    GW_TENANT_0001("GW-TENANT-0001", 400, false, "gw-tenant-missing", "error.tenant.missing", "4.6.0"),

    /**
     * 租户信息无效（已过期、被禁用、状态异常等）。
     */
    GW_TENANT_0002("GW-TENANT-0002", 403, false, "gw-tenant-invalid", "error.tenant.invalid", "4.6.0"),

    /**
     * 请求过于频繁，触发限流。
     */
    GW_RATE_0001("GW-RATE-0001", 429, true, "gw-rate-limited", "error.rate.limited", "4.6.0"),

    /**
     * 上游服务不可用（连接失败、5xx 等）。
     */
    GW_UPSTREAM_0001("GW-UPSTREAM-0001", 502, true, "gw-upstream-unavailable", "error.upstream.unavailable", "4.6.0"),

    /**
     * 上游服务响应超时。
     */
    GW_UPSTREAM_0002("GW-UPSTREAM-0002", 504, true, "gw-upstream-timeout", "error.upstream.timeout", "4.6.0"),

    /**
     * 系统内部错误（兜底错误码）。所有未分类的 Java 异常一律映射到本码，
     * 不暴露具体异常类名、SQL、堆栈等敏感信息。
     */
    GW_SYSTEM_0001("GW-SYSTEM-0001", 500, false, "gw-system-internal-error", "error.system.internal", "4.6.0"),

    /**
     * 路由未匹配（Spring Cloud Gateway 找不到对应下游）。
     */
    GW_ROUTE_0001("GW-ROUTE-0001", 404, false, "gw-route-not-found", "error.route.not.found", "4.6.0"),

    /**
     * 请求参数错误（反序列化失败、字段校验失败等）。
     */
    GW_REQ_0001("GW-REQ-0001", 400, false, "gw-req-bad-request", "error.req.bad.request", "4.6.0");

    /**
     * 错误码字符串（如 {@code GW-AUTH-0001}）。
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
     * <p>
     * 详情页 URL 形如 {@code /gateway/errors/{docSlug}}；列表页 / JSON 清单
     * 直接使用本字段做锚点 / slug。
     * </p>
     */
    private final String docSlug;

    /**
     * i18n 键空间根（如 {@code error.auth.token.invalid}）。
     * <p>
     * 本键空间下挂载 4 个子键：
     * {@code .meaning}（含义）、{@code .cause}（常见原因）、
     * {@code .investigate}（排查方向）、{@code .retry}（是否可重试）。
     * </p>
     */
    private final String i18nKey;

    /**
     * 错误码协议版本。
     */
    private final String version;

    GatewayErrorCode(String errorCode, int httpStatus, boolean retryable, String docSlug, String i18nKey, String version) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.docSlug = docSlug;
        this.i18nKey = i18nKey;
        this.version = version;
    }

    /**
     * 返回稳定错误码字符串。
     *
     * @return 错误码字符串（如 {@code GW-AUTH-0001}）
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
     * @return true 表示客户端可以重试
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 返回文档相对路径段。
     *
     * @return slug（如 {@code gw-auth-token-invalid}）
     */
    public String getDocSlug() {
        return docSlug;
    }

    /**
     * 返回 i18n 键空间根。
     *
     * @return i18nKey（如 {@code error.auth.token.invalid}）
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
     * 根据错误码字符串查找枚举实例（不区分大小写）。
     *
     * @param code 错误码字符串
     * @return 对应的枚举实例，若未匹配返回 {@code null}
     */
    public static GatewayErrorCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (GatewayErrorCode value : values()) {
            if (value.errorCode.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
