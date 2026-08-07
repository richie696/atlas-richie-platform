package cn.richie696.component.oauth.contract;

/**
 * OAuth grant_type 字面量常量, 涵盖 authorization_code、client_credentials、refresh_token 以及 RFC 8628 device_code。
 * <p>
 * 处于 OAuth 契约层的 grant_type 词表一环, 被契约层 wire 模型、token endpoint 请求/响应以及下游 core / authz / dcr 中的 grant 处理器共同引用, 是分派授权流时的唯一判别依据。
 * 解决"字符串硬编码导致多源校验容易拼错、扩展新 grant_type 时漏改一处"的问题, 为协议扩展点保留稳定的中心化常量。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OAuthGrantTypes {

    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    private OAuthGrantTypes() {
    }
}
