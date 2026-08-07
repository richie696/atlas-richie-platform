package cn.richie696.component.oauth.gateway;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** 可选的 WebFlux 过滤器外观；Gateway 工程可在自己的路由策略中决定是否启用。 */
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
