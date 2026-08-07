package cn.richie696.component.oauth.oidc;

import java.net.URI;
import java.util.List;

/** 客户端注册的 OIDC 前/后通道注销端点。 */
public record OidcClientLogoutConfiguration(
        String clientId,
        URI frontchannelLogoutUri,
        URI backchannelLogoutUri,
        List<String> postLogoutRedirectUris) {

    public OidcClientLogoutConfiguration {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        postLogoutRedirectUris = postLogoutRedirectUris == null
                ? List.of() : List.copyOf(postLogoutRedirectUris);
    }
}
