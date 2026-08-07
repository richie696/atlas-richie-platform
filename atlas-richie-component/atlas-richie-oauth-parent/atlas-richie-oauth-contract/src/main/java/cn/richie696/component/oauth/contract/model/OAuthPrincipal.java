package cn.richie696.component.oauth.contract.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Resource Server 校验成功后向 Gateway/业务服务输出的可信主体。 */
public record OAuthPrincipal(
        String subject,
        String clientId,
        String issuer,
        String audience,
        String tokenId,
        List<String> scopes,
        Map<String, Object> claims
) {
    public OAuthPrincipal {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }

    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}
