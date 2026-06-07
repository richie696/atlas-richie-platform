package com.richie.gateway.filter.common.infrastructure;

import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.i18n.resolver.I18nResolver;
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
@DisplayName("I18nFilter")
class I18nFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private I18nProperties i18nProperties;

    @Mock
    private GatewayFilterChain filterChain;

    private I18nFilter i18nFilter;

    @BeforeEach
    void setUp() {
        i18nFilter = new I18nFilter(gatewayConfig, i18nResolver, i18nProperties);
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTest {

        @Test
        @DisplayName("should return -1000 (I18N_FILTER order)")
        void returnsCorrectOrder() {
            assertThat(i18nFilter.getOrder()).isEqualTo(-1000);
        }
    }

    @Nested
    @DisplayName("filter()")
    class FilterTest {

        @Test
        @DisplayName("should process request through filter chain")
        void processesRequest() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            i18nFilter.filter(exchange, filterChain);

            verify(filterChain).filter(exchange);
        }
    }
}
