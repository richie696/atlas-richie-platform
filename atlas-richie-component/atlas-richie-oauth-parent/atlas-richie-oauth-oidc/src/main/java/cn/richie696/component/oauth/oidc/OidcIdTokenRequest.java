package cn.richie696.component.oauth.oidc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 由 OAuth Service 在完成用户认证和同意后提交给 ID Token 服务的领域请求。 */
public record OidcIdTokenRequest(
        String subject,
        String clientId,
        String nonce,
        Instant authenticationTime,
        Collection<String> scopes,
        Map<String, Object> claims,
        String accessToken
) {
    public OidcIdTokenRequest {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }

    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}
