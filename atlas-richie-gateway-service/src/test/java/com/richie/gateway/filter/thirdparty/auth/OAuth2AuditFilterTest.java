package com.richie.gateway.filter.thirdparty.auth;

import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.thirdparty.auth.OAuth2AuditFilter;
import com.richie.gateway.service.AuditService;
import com.richie.gateway.service.OAuth2ClientService;
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
@DisplayName("OAuth2AuditFilter")
class OAuth2AuditFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private AuditService auditService;

    @Mock
    private OAuth2ClientService clientService;

    private OAuth2AuditFilter oauth2AuditFilter;

    @BeforeEach
    void setUp() {
        oauth2AuditFilter = new OAuth2AuditFilter(gatewayConfig, i18nResolver, auditService, clientService);
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTest {

        @Test
        @DisplayName("should return 402 (OAUTH2_AUDIT_FILTER order)")
        void returnsCorrectOrder() {
            assertThat(oauth2AuditFilter.getOrder()).isEqualTo(402);
        }
    }

    @Nested
    @DisplayName("filter() - non-OAuth2 paths")
    class NonOAuth2PathsTest {

        @Test
        @DisplayName("should pass through for non-OAuth2 paths")
        void passesThroughForNonOAuth2Paths() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            oauth2AuditFilter.filter(exchange, filterChain);

            verify(filterChain).filter(exchange);
        }
    }
}
