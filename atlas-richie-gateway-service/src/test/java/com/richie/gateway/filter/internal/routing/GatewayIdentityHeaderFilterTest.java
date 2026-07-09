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
package com.richie.gateway.filter.internal.routing;

import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.config.GatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GatewayIdentityHeaderFilter 测试。
 * <p>
 * 验证：
 * <ol>
 *   <li>filter 给每个请求加 {@code X-Forwarded-From-Gateway} header</li>
 *   <li>gateway-id 格式严格为 {@code env:cluster:instance}（{@code :} 分隔）</li>
 *   <li>env 从 spring.profiles.active 取第一个；空时回退 {@code prod}</li>
 *   <li>cluster 从 {@code PLATFORM_GATEWAY_CLUSTER} / {@code CLUSTER_NAME} 环境变量取；空时回退 {@code default}</li>
 *   <li>instance 从 {@code HOSTNAME} 环境变量取；空时回退本机 hostname</li>
 *   <li>原始 exchange 不被 mutate（immutable contract）</li>
 *   <li>filter 是无状态行为（多次调用 id 不变）</li>
 * </ol>
 */
class GatewayIdentityHeaderFilterTest {

    private GatewayIdentityHeaderFilter filter;

    @BeforeEach
    void setUp() {
        GatewayConfig config = mock(GatewayConfig.class);
        I18nResolver i18n = mock(I18nResolver.class);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        filter = new GatewayIdentityHeaderFilter(config, i18n, env);
    }

    @Test
    void addsHeader_withFormatEnvColonClusterColonInstance() {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayIdentityHeaderFilter.HEADER_NAME))
                .matches("^[\\w.-]+:[\\w.-]+:[\\w.-]+$");
    }

    @Test
    void envTakenFromActiveProfiles() {
        GatewayIdentityHeaderFilter local = newFilter("staging", null, "host-1");
        String id = extractId(local);
        assertThat(id).startsWith("staging:");
    }

    @Test
    void envFallsBackToProdWhenNoProfile() {
        GatewayIdentityHeaderFilter local = newFilter(null, null, "host-1");
        String id = extractId(local);
        assertThat(id).startsWith("prod:");
    }

    @Test
    void clusterTakenFromEnvVar() {
        GatewayIdentityHeaderFilter local = newFilter("dev", "cluster-a", "host-1");
        String id = extractId(local);
        String[] parts = id.split(":");
        assertThat(parts).hasSize(3);
        assertThat(parts[1]).isEqualTo("cluster-a");
    }

    @Test
    void clusterFallsBackToDefaultWhenEnvVarBlank() {
        GatewayIdentityHeaderFilter local = newFilter("dev", " ", "host-1");
        String id = extractId(local);
        String[] parts = id.split(":");
        assertThat(parts[1]).isEqualTo("default");
    }

    @Test
    void instanceTakenFromHostname() {
        GatewayIdentityHeaderFilter local = newFilter("dev", null, "gateway-7d4f-jx9k2");
        String id = extractId(local);
        String[] parts = id.split(":");
        assertThat(parts[2]).isEqualTo("gateway-7d4f-jx9k2");
    }

    @Test
    void instanceFallsBackToLocalHostname() {
        GatewayIdentityHeaderFilter local = newFilter("dev", null, null);
        String id = extractId(local);
        String[] parts = id.split(":");
        assertThat(parts[2]).isNotBlank();
    }

    @Test
    void originalExchange_notMutated() {
        ServerWebExchange original = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));
        assertThat(original.getRequest().getHeaders().getFirst(GatewayIdentityHeaderFilter.HEADER_NAME))
                .as("original exchange must not have header before filter runs")
                .isNull();

        GatewayFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(original, chain)).verifyComplete();

        assertThat(original.getRequest().getHeaders().getFirst(GatewayIdentityHeaderFilter.HEADER_NAME))
                .as("original exchange must remain unmodified (ServerWebExchange is immutable)")
                .isNull();
    }

    @Test
    void gatewayId_isCachedAndStable() {
        AtomicReference<String> id1 = new AtomicReference<>();
        AtomicReference<String> id2 = new AtomicReference<>();

        GatewayFilterChain chain1 = ex -> {
            id1.set(ex.getRequest().getHeaders().getFirst(GatewayIdentityHeaderFilter.HEADER_NAME));
            return Mono.empty();
        };
        GatewayFilterChain chain2 = ex -> {
            id2.set(ex.getRequest().getHeaders().getFirst(GatewayIdentityHeaderFilter.HEADER_NAME));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(
                MockServerWebExchange.from(MockServerHttpRequest.get("/a")), chain1)).verifyComplete();
        StepVerifier.create(filter.filter(
                MockServerWebExchange.from(MockServerHttpRequest.get("/b")), chain2)).verifyComplete();

        assertThat(id1.get()).isEqualTo(id2.get());
    }

    @Test
    void getOrder_returns451() {
        assertThat(filter.getOrder()).isEqualTo(451);
    }

    @Test
    void enableVerifyFilter_alwaysReturnsTrue() {
        assertThat(filter.enableVerifyFilter(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")))).isTrue();
    }

    private GatewayIdentityHeaderFilter newFilter(String profile, String cluster, String hostname) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) {
            env.setActiveProfiles(profile);
        }
        if (cluster != null) {
            env.setProperty("PLATFORM_GATEWAY_CLUSTER", cluster);
            if (hostname != null) {
                env.setProperty("HOSTNAME", hostname);
            }
        } else if (hostname != null) {
            env.setProperty("HOSTNAME", hostname);
        }
        return new GatewayIdentityHeaderFilter(
                mock(GatewayConfig.class),
                mock(I18nResolver.class),
                env);
    }

    private String extractId(GatewayIdentityHeaderFilter f) {
        AtomicReference<String> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex.getRequest().getHeaders().getFirst(GatewayIdentityHeaderFilter.HEADER_NAME));
            return Mono.empty();
        };
        StepVerifier.create(f.filter(
                MockServerWebExchange.from(MockServerHttpRequest.get("/probe")), chain)).verifyComplete();
        return captured.get();
    }
}