package cn.richie696.component.oauth.core.model;

/**
 * 已由 HTTP 适配层规范化的 OAuth Client Authentication 请求。
 * <p>
 * 表达协议语义({@code client_id} / {@code client_secret} / 认证方式),不再携带 HTTP 层面的 Basic、
 * 表单参数、Header 等表达方式;由 OAuth Service 在 HTTP 适配层解析后传入
 * {@link ClientAuthenticationService},新增认证方式(mTLS、JWT assertion 等)不会扩散到协议层。
 * </p>
 * <p>
 * 处于 oauth-core 的协议输入位置:由 HTTP 适配层构造,被 {@link ClientAuthenticationService}
 * 消费;同时在 TokenEndpoint、AuthorizationCodeGrant 等需要客户端认证的协议入口复用。
 * </p>
 * <p>
 * 解决的问题:让 Client Authentication 的"协议含义"和"HTTP 表达"解耦,新增 mTLS / private_key_jwt
 * 等认证方式只需在适配层补全,核心校验逻辑不动。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public record ClientAuthenticationRequest(
        String clientId,
        String clientSecret,
        String method
) {

    public static ClientAuthenticationRequest clientSecretPost(String clientId, String clientSecret) {
        return new ClientAuthenticationRequest(clientId, clientSecret, "client_secret_post");
    }

    public static ClientAuthenticationRequest clientSecretBasic(String clientId, String clientSecret) {
        return new ClientAuthenticationRequest(clientId, clientSecret, "client_secret_basic");
    }

    public static ClientAuthenticationRequest publicClient(String clientId) {
        return new ClientAuthenticationRequest(clientId, null, "none");
    }
}
