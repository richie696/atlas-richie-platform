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
package cn.richie696.component.tenant.gateway.filter;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import cn.richie696.context.utils.spring.JwtUtils;
import cn.richie696.contract.model.LoginUserPrincipal;
import cn.richie696.component.tenant.gateway.spi.AccessTokenRevoker;
import cn.richie696.component.tenant.gateway.spi.TenantErrorResponder;
import cn.richie696.component.tenant.gateway.spi.TenantExpiredNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantFilterTest {

    private static final String SECRET = "tenant-filter-test-secret";

    private MultiTenancyProperties config;
    private TenantErrorResponder errorResponder;
    private RecordingNotifier notifier;
    private RecordingRevoker revoker;
    private TenantFilter filter;

    @BeforeEach
    void setUp() {
        config = new MultiTenancyProperties();
        config.getGateway().setIdentityAssertionSecret(SECRET);
        errorResponder = (exchange, messageKey) -> Mono.empty();
        notifier = new RecordingNotifier(true);
        revoker = new RecordingRevoker();
        filter = new TenantFilter(config, errorResponder, notifier, revoker);
    }

    @Test
    void disabled_doesNotInspectOrMutateRequest() {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        config.setEnable(false);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header("X-Tenant-ID", "attacker-value")
                        .build());

        StepVerifier.create(filter.filter(exchange, ex -> {
            captured.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Tenant-ID"))
                .isEqualTo("attacker-value");
        assertThat(notifier.tenantId).isNull();
        assertThat(revoker.token).isNull();
    }

    @Test
    void expiredTenant_notifiesAndRevokesThroughPorts() {
        config.setEnable(true);
        notifier.accept = true;
        String token = tenantToken("1001", Instant.now().minusSeconds(1).getEpochSecond());

        StepVerifier.create(filter.filter(exchangeWithToken(token), ex -> Mono.empty()))
                .verifyComplete();

        assertThat(notifier.tenantId).isEqualTo("1001");
        assertThat(revoker.token).isEqualTo(token);
    }

    @Test
    void expiredTenant_whenNotificationFails_doesNotRevokeToken() {
        config.setEnable(true);
        notifier.accept = false;
        String token = tenantToken("1001", Instant.now().minusSeconds(1).getEpochSecond());

        StepVerifier.create(filter.filter(exchangeWithToken(token), ex -> Mono.empty()))
                .verifyComplete();

        assertThat(notifier.tenantId).isEqualTo("1001");
        assertThat(revoker.token).isNull();
    }

    @Test
    void activeTenant_writesOnlyGatewayTenantHeaders() {
        config.setEnable(true);
        String token = tenantToken("1001", Instant.now().plusSeconds(60).getEpochSecond());
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(
                exchangeWithTokenAndHeaders(token), ex -> {
                    captured.set(ex);
                    return Mono.empty();
                })).verifyComplete();

        assertThat(captured.get().getRequest().getHeaders().getFirst("x-rd-request-tenantid"))
                .isEqualTo("1001");
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Tenant-ID"))
                .isNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Tenant-Assertion"))
                .isNotBlank();
        assertThat(notifier.tenantId).isNull();
        assertThat(revoker.token).isNull();
    }

    private ServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(JwtUtils.X_ACCESS_TOKEN, token)
                        .build());
    }

    private ServerWebExchange exchangeWithTokenAndHeaders(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(JwtUtils.X_ACCESS_TOKEN, token)
                        .header("X-Tenant-ID", "attacker-value")
                        .header("x-rd-request-tenantid", "attacker-value")
                        .header("X-Tenant-Assertion", "attacker-assertion")
                        .build());
    }

    private String tenantToken(String tenantId, long tenantExpiredAt) {
        LoginUserPrincipal principal = new LoginUserPrincipal()
                .setUsername("user-1")
                .setTenantEnabled(true);
        principal.addParam("tenantId", tenantId);
        principal.addParam("tenantExpiredTime", String.valueOf(tenantExpiredAt));
        return JwtUtils.generateJwtToken(principal, SECRET, System.currentTimeMillis() + 60_000);
    }

    private static final class RecordingNotifier implements TenantExpiredNotifier {
        private boolean accept;
        private String tenantId;

        private RecordingNotifier(boolean accept) {
            this.accept = accept;
        }

        @Override
        public Mono<Boolean> notifyExpired(String tenantId) {
            this.tenantId = tenantId;
            return Mono.just(accept);
        }
    }

    private static final class RecordingRevoker implements AccessTokenRevoker {
        private String token;

        @Override
        public Mono<Void> revoke(String token) {
            this.token = token;
            return Mono.empty();
        }
    }
}
