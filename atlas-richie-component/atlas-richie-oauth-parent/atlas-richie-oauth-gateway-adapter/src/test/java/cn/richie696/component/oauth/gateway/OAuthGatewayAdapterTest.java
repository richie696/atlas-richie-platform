package cn.richie696.component.oauth.gateway;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import cn.richie696.component.oauth.resource.ResourceServerAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthGatewayAdapterTest {

    @Test
    void authenticateAndPropagateReplacesUntrustedIdentityHeaders() {
        OAuthPrincipal principal = new OAuthPrincipal("user-1", "client-1", "issuer",
                "api", "jti-1", List.of("read"), Map.of("tenant_id", "tenant-1"));
        ResourceServerAuthenticator authenticator = new ResourceServerAuthenticator(
                token -> principal, null, false);
        OAuthGatewayAdapter adapter = new OAuthGatewayAdapter(authenticator);

        assertThat(adapter.authenticate("Bearer token")).isEqualTo(principal);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(OAuthGatewayAdapter.SUBJECT_HEADER, "attacker")
                .build());
        ServerWebExchange propagated = adapter.propagate(exchange, principal);

        assertThat(propagated.getRequest().getHeaders().getFirst(OAuthGatewayAdapter.SUBJECT_HEADER))
                .isEqualTo("user-1");
        assertThat(propagated.getRequest().getHeaders().getFirst(OAuthGatewayAdapter.SCOPE_HEADER))
                .isEqualTo("read");
        assertThat(propagated.getRequest().getHeaders().getFirst(OAuthGatewayAdapter.TENANT_ID_HEADER))
                .isEqualTo("tenant-1");
    }

    @Test
    void webFilterReturnsBearerChallengeOnAuthenticationFailure() {
        ResourceServerAuthenticator authenticator = new ResourceServerAuthenticator(token -> {
            throw new RuntimeException("invalid");
        }, null, false);
        OAuthGatewayWebFilter filter = new OAuthGatewayWebFilter(new OAuthGatewayAdapter(authenticator));
        WebFilterChain chain = exchange -> Mono.empty();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header("Authorization", "Bearer bad").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(OAuthGatewayWebFilter.WWW_AUTHENTICATE))
                .isEqualTo("Bearer");
    }
}
