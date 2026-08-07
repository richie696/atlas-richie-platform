package cn.richie696.component.oauth.gateway;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import cn.richie696.component.oauth.resource.ResourceServerAuthenticator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/** Gateway 只依赖的 Resource Server Facade。 */
public class OAuthGatewayAdapter {

    public static final String SUBJECT_HEADER = "X-Authenticated-Subject";
    public static final String CLIENT_ID_HEADER = "X-Authenticated-Client-Id";
    public static final String ISSUER_HEADER = "X-Authenticated-Issuer";
    public static final String SCOPE_HEADER = "X-Authenticated-Scopes";
    public static final String TOKEN_ID_HEADER = "X-Authenticated-Token-Id";
    public static final String TENANT_ID_HEADER = "X-Authenticated-Tenant-Id";

    private final ResourceServerAuthenticator authenticator;

    public OAuthGatewayAdapter(ResourceServerAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public OAuthPrincipal authenticate(String authorizationHeader) {
        String token = BearerTokenExtractor.extract(authorizationHeader);
        if (token == null) {
            throw new IllegalArgumentException("缺少有效的 Bearer Token");
        }
        return authenticator.authenticate(token);
    }

    /** 同时支持 Bearer + DPoP proof 以及 RFC 9449 的 Authorization: DPoP。 */
    public OAuthPrincipal authenticate(String authorizationHeader, String dpopProof,
                                      String method, URI requestUri) {
        String token = BearerTokenExtractor.extract(authorizationHeader);
        if (token == null) {
            token = DpopTokenExtractor.extract(authorizationHeader);
        }
        if (token == null) {
            throw new IllegalArgumentException("缺少有效的 OAuth Token");
        }
        return authenticator.authenticate(token, method, requestUri, dpopProof);
    }

    public Mono<OAuthPrincipal> authenticateReactive(String authorizationHeader) {
        return Mono.fromCallable(() -> authenticate(authorizationHeader));
    }

    /** 返回附带可信主体头的 exchange，Gateway Filter 可将其传给下游。 */
    public ServerWebExchange propagate(ServerWebExchange exchange, OAuthPrincipal principal) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            replace(headers, SUBJECT_HEADER, principal.subject());
            replace(headers, CLIENT_ID_HEADER, principal.clientId());
            replace(headers, ISSUER_HEADER, principal.issuer());
            replace(headers, SCOPE_HEADER, String.join(" ", principal.scopes()));
            replace(headers, TOKEN_ID_HEADER, principal.tokenId());
            Object tenantId = principal.claims().get("tenant_id");
            replace(headers, TENANT_ID_HEADER, tenantId == null ? null : tenantId.toString());
        })).build();
    }

    private void replace(org.springframework.http.HttpHeaders headers, String name, String value) {
        headers.remove(name);
        if (value != null && !value.isBlank()) {
            headers.add(name, value);
        }
    }
}
