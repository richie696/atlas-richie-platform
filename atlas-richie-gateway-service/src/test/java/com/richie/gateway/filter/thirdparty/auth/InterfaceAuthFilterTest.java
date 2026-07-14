/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.filter.thirdparty.auth;

import com.richie.component.oauth.core.ScopeResolver;
import com.richie.component.oauth.core.TokenEndpoint;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.thirdparty.auth.InterfaceAuthFilter;
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
@DisplayName("InterfaceAuthFilter")
class InterfaceAuthFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private TokenEndpoint tokenEndpoint;

    @Mock
    private OAuth2Properties oAuth2Properties;

    @Mock
    private AuditService auditService;

    @Mock
    private ScopeResolver scopeResolver;

    @Mock
    private OAuth2Properties oauth2Properties;

    private InterfaceAuthFilter interfaceAuthFilter;

    @BeforeEach
    void setUp() {
        interfaceAuthFilter = new InterfaceAuthFilter(gatewayConfig, i18nResolver, tokenEndpoint, auditService, scopeResolver, oAuth2Properties);
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
            when(gatewayConfig.getOauth2()).thenReturn(oauth2Properties);
            when(oauth2Properties.isEnabled()).thenReturn(true);
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource").build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            assertThat(interfaceAuthFilter.enableVerifyFilter(exchange)).isTrue();
        }

        @Test
        @DisplayName("should return false when OAuth2 disabled")
        void returnsFalseWhenDisabled() {
            when(gatewayConfig.getOauth2()).thenReturn(oauth2Properties);
            when(oauth2Properties.isEnabled()).thenReturn(false);
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
