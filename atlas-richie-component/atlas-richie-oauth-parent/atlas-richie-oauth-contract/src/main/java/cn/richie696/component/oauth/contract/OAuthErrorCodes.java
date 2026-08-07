package cn.richie696.component.oauth.contract;

/**
 * OAuth 标准错误码字面量集合, 与 RFC 6749 / RFC 7009 / RFC 8628 / RFC 7662 中的 error 字段保持一致。
 * <p>
 * 处于 OAuth 契约层的 wire 词表一环, 与 {@link OAuth2Constants} 中的错误码字段互补, 分别承担"协议内核常量"与"授权 / 吊销 / 设备授权 / 内省场景错误码"的标准化职责, 被异常体系与 endpoint 出参共同引用。
 * 解决"错误码字面量散落在业务代码里、不同子模块写法不一致"的问题, 让 error 响应体生成逻辑共享同一份受控字典, 避免协议违规。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OAuthErrorCodes {

    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_CLIENT = "invalid_client";
    public static final String INVALID_GRANT = "invalid_grant";
    public static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    public static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    public static final String INVALID_SCOPE = "invalid_scope";
    public static final String INVALID_TARGET = "invalid_target";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String SERVER_ERROR = "server_error";
    public static final String UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type";
    public static final String INVALID_TOKEN = "invalid_token";
    public static final String EXPIRED_TOKEN = "expired_token";
    public static final String AUTHORIZATION_PENDING = "authorization_pending";
    public static final String SLOW_DOWN = "slow_down";

    private OAuthErrorCodes() {
    }
}
