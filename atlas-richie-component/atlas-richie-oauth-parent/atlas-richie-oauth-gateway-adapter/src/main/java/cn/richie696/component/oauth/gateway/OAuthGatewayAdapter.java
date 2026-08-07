package cn.richie696.component.oauth.gateway;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import cn.richie696.component.oauth.resource.ResourceServerAuthenticator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 网关层与 Resource Server 之间的边界 Facade，也是 Gateway 工程唯一需要依赖的 OAuth 网关门面。
 *
 * <p>职责链位置：处于网关入口（{@link OAuthGatewayWebFilter} 或业务自定义 WebFilter）
 * 与 {@link ResourceServerAuthenticator} 之间，把协议头部解析、token 提取、DPoP proof 透传、
 * 认证结果到下游 {@code X-Authenticated-*} 头的写入这一连串动作聚合在一个类里。
 * 它屏蔽了 Resource Server 的 JWT / introspection / DPoP 内部细节，
 * 让网关层只看见 {@link OAuthPrincipal}。</p>
 *
 * <p>解决以下问题：网关既要支持 Bearer 与 RFC 9449 DPoP 两种凭证形态，
 * 又要避免把认证内部实现细节泄漏到网关路由层；该 Facade 同时承担凭证解析、
 * 主体验证与下游可信头的传播（subject / clientId / issuer / scopes / tokenId / tenantId），
 * 让业务 Filter 可以零成本完成"取出凭证 → 验证 → 写入上下文"三步链路。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
