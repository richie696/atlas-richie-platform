package cn.richie696.component.oauth.core.model;

/**
 * 已由 HTTP 适配层规范化的 OAuth Client Authentication 请求。
 * <p>Basic、POST 等 HTTP 表达方式由服务适配层解析，这里只保留协议语义。</p>
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
