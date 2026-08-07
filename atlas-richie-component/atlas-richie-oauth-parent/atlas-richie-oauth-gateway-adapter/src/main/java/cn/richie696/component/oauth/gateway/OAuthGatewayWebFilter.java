package cn.richie696.component.oauth.gateway;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 基于 WebFlux 的网关 OAuth 边缘过滤器外观，作为 OAuth 在网关工程中的"开箱即用"接入点。
 *
 * <p>职责链位置：处于 Spring WebFlux 请求链的最前端（在业务路由之前），它把
 * {@link OAuthGatewayAdapter} 的 Bearer / DPoP 认证能力转译为标准的 WebFlux
 * {@link WebFilter} 行为：拉取 {@code Authorization} / {@code DPoP} 头部、调用 Adapter、
 * 在成功后把可信主体写入请求头并放行，失败则统一返回
 * {@code 401 + WWW-Authenticate: Bearer}。业务网关可在自己路由策略中决定是否启用它。</p>
 *
 * <p>解决以下问题：网关工程不想自己写 WebFlux 适配样板代码，又要求 OAuth 接入与
 * 业务路由解耦；通过该可选过滤器，业务方只需注册一个 Bean 即可获得 RFC 6750 /
 * RFC 9449 边缘认证，又不会被强制绑定到具体的网关框架（如 Spring Cloud Gateway
 * 或自定义路由），替换成本仅为一行配置。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public class OAuthGatewayWebFilter implements WebFilter {

    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private final OAuthGatewayAdapter adapter;

    public OAuthGatewayWebFilter(OAuthGatewayAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        String dpopProof = exchange.getRequest().getHeaders().getFirst("DPoP");
        return Mono.fromCallable(() -> adapter.authenticate(authorization, dpopProof,
                        exchange.getRequest().getMethod().name(), exchange.getRequest().getURI()))
                .flatMap(principal -> chain.filter(adapter.propagate(exchange, principal)))
                .onErrorResume(error -> unauthorized(exchange));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(WWW_AUTHENTICATE, "Bearer");
        return exchange.getResponse().setComplete();
    }
}
