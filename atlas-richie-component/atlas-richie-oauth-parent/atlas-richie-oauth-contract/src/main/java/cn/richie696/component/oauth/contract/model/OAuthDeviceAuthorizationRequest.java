package cn.richie696.component.oauth.contract.model;

import java.util.List;

/** RFC 8628 Device Authorization Grant 请求。 */
public record OAuthDeviceAuthorizationRequest(
        String clientId,
        List<String> scopes,
        String resource
) {
    public OAuthDeviceAuthorizationRequest {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
