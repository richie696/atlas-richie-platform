package com.richie.gateway.filter.thirdparty.auth;

import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.config.IOAuthFilterConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.thirdparty.auth.InterfaceAuthFilter;
import com.richie.gateway.service.AuditService;
import com.richie.gateway.service.OAuth2AuthService;
import com.richie.gateway.service.OAuth2ScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterfaceAuthFilter")
class InterfaceAuthFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private OAuth2AuthService authService;

    @Mock
    private AuditService auditService;

    @Mock
    private OAuth2ScopeService scopeService;

    @Mock
    private IOAuthFilterConfig interfaceAuthConfig;

    private InterfaceAuthFilter interfaceAuthFilter;

    @BeforeEach
    void setUp() {
        interfaceAuthFilter = new InterfaceAuthFilter(gatewayConfig, i18nResolver, authService, auditService, scopeService);
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTest {

        @Test
        @DisplayName("should return 400 (INTERFACE_AUTH_FILTER order)")
        void returnsCorrectOrder() {
            assertThat(interfaceAuthFilter.getOrder()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("enableVerifyFilter")
    class EnableVerifyFilterTest {

        @Test
        @DisplayName("should return false for OAuth2 base paths")
        void returnsFalseForOAuth2Paths() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/oauth2/token").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            assertThat(interfaceAuthFilter.enableVerifyFilter(exchange)).isFalse();
        }

        @Test
        @DisplayName("should return true for non-OAuth2 paths when enabled")
        void returnsTrueForNonOAuth2PathsWhenEnabled() {
            when(gatewayConfig.getInterfaceAuth()).thenReturn(interfaceAuthConfig);
            when(interfaceAuthConfig.isEnable()).thenReturn(true);
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            assertThat(interfaceAuthFilter.enableVerifyFilter(exchange)).isTrue();
        }

        @Test
        @DisplayName("should return false when interface auth disabled")
        void returnsFalseWhenDisabled() {
            when(gatewayConfig.getInterfaceAuth()).thenReturn(interfaceAuthConfig);
            when(interfaceAuthConfig.isEnable()).thenReturn(false);
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            assertThat(interfaceAuthFilter.enableVerifyFilter(exchange)).isFalse();
        }
    }

    @Nested
    @DisplayName("filter() - OAuth2 paths")
    class OAuth2PathsTest {

        @Test
        @DisplayName("should pass through for /api/oauth2/token")
        void passesThroughForTokenPath() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/oauth2/token").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            interfaceAuthFilter.filter(exchange, filterChain);

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("should pass through for /api/oauth2/revoke")
        void passesThroughForRevokePath() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/oauth2/revoke").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            interfaceAuthFilter.filter(exchange, filterChain);

            verify(filterChain).filter(exchange);
        }
    }
}
