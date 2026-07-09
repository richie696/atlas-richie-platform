/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.filter.internal.routing;

import com.richie.gateway.config.GatewayConfig;
import com.richie.contract.gateway.config.DeployConfig;
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
@DisplayName("CanaryIdExtractorFilter")
class CanaryIdExtractorFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private DeployConfig deployConfig;

    @Mock
    private GatewayFilterChain filterChain;

    private CanaryIdExtractorFilter canaryIdExtractorFilter;

    @BeforeEach
    void setUp() {
        canaryIdExtractorFilter = new CanaryIdExtractorFilter(gatewayConfig, i18nResolver);
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTest {

        @Test
        @DisplayName("should return 450 (CANARY_ID_EXTRACTOR_FILTER order)")
        void returnsCorrectOrder() {
            assertThat(canaryIdExtractorFilter.getOrder()).isEqualTo(450);
        }
    }

    @Nested
    @DisplayName("enableVerifyFilter")
    class EnableVerifyFilterTest {

        @Test
        @DisplayName("should return true when deploy enabled")
        void returnsTrueWhenEnabled() {
            when(gatewayConfig.getDeploy()).thenReturn(deployConfig);
            when(deployConfig.isEnable()).thenReturn(true);

            assertThat(canaryIdExtractorFilter.enableVerifyFilter(null)).isTrue();
        }

        @Test
        @DisplayName("should return false when deploy disabled")
        void returnsFalseWhenDisabled() {
            when(gatewayConfig.getDeploy()).thenReturn(deployConfig);
            when(deployConfig.isEnable()).thenReturn(false);

            assertThat(canaryIdExtractorFilter.enableVerifyFilter(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("filter()")
    class FilterTest {

        @Test
        @DisplayName("should pass through when deploy disabled")
        void passesThroughWhenDisabled() {
            when(gatewayConfig.getDeploy()).thenReturn(deployConfig);
            when(deployConfig.isEnable()).thenReturn(false);
            MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            canaryIdExtractorFilter.filter(exchange, filterChain);

            verify(filterChain).filter(exchange);
        }
    }
}
