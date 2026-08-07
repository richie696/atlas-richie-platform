package cn.richie696.component.oauth.core.model;

/** 客户端认证结果。 */
public record ClientAuthenticationResult(boolean authenticated, ClientConfig client, String errorCode) {

    public static ClientAuthenticationResult success(ClientConfig client) {
        return new ClientAuthenticationResult(true, client, null);
    }

    public static ClientAuthenticationResult failure(String errorCode) {
        return new ClientAuthenticationResult(false, null, errorCode);
    }
}
