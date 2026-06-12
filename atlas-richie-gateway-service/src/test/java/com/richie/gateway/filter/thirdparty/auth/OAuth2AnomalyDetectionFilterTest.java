package com.richie.gateway.filter.thirdparty.auth;

import com.richie.component.oauth.core.ClientRegistry;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.config.OAuth2AnomalyDetectionConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.common.security.AnomalyDetectionFilter;
import com.richie.gateway.filter.thirdparty.auth.OAuth2AnomalyDetectionFilter;
import com.richie.gateway.service.AuditService;
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
@DisplayName("OAuth2AnomalyDetectionFilter")
class OAuth2AnomalyDetectionFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private ClientRegistry clientRegistry;

    @Mock
    private AuditService auditService;

    @Mock
    private OAuth2AnomalyDetectionConfig detectionConfig;

    @Mock
    private AnomalyDetectionFilter commonAnomalyDetectionFilter;

    private OAuth2AnomalyDetectionFilter oauth2AnomalyDetectionFilter;

    @BeforeEach
    void setUp() {
        oauth2AnomalyDetectionFilter = new OAuth2AnomalyDetectionFilter(
                gatewayConfig, i18nResolver, clientRegistry, auditService, detectionConfig, commonAnomalyDetectionFilter);
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTest {

        @Test
        @DisplayName("should return 401 (OAUTH2_ANOMALY_DETECTION_FILTER order)")
        void returnsCorrectOrder() {
            assertThat(oauth2AnomalyDetectionFilter.getOrder()).isEqualTo(401);
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

            oauth2AnomalyDetectionFilter.filter(exchange, filterChain);

            verify(filterChain).filter(exchange);
        }
    }
}
