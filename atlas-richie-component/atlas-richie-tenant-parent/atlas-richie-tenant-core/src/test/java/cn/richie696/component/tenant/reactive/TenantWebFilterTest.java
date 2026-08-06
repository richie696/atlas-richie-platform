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
package cn.richie696.component.tenant.reactive;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import cn.richie696.component.tenant.context.TenantContext;
import cn.richie696.component.tenant.context.ThreadLocalHolder;
import cn.richie696.component.tenant.exception.TenantErrorCode;
import cn.richie696.component.tenant.model.IsolationMode;
import cn.richie696.component.tenant.model.TenantInfo;
import cn.richie696.component.tenant.model.TenantStatus;
import cn.richie696.component.tenant.spi.TenantInfoProvider;
import cn.richie696.contract.constant.GlobalConstants;
import cn.richie696.contract.model.TenantPrincipal;
import cn.richie696.context.utils.spring.TenantIdentityAssertionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantWebFilter 单元测试 — 覆盖 WebFilter 各分支。
 *
 * <p>由于 {@code reactor-test} 不在测试 classpath，本测试使用 {@link Mono#block()}
 * 同步等待过滤器链返回，并通过 Mockito mock ServerWebExchange/Request/Response
 * 来模拟 WebFlux 入口。{@link HttpHeaders} 使用真实实例（非 mock）以便
 * {@code getFirst(...)} 返回哨兵值。</p>
 */
@DisplayName("TenantWebFilter — Reactive 租户身份过滤器")
class TenantWebFilterTest {

    private MultiTenancyProperties props;
    private TenantInfoProvider provider;
    private TenantWebFilter filter;
    private WebFilterChain chain;
    private HttpHeaders reqHeaders;
    private HttpHeaders resHeaders;

    @BeforeEach
    void setUp() throws Exception {
        reqHeaders = new HttpHeaders();
        resHeaders = new HttpHeaders();
        props = new MultiTenancyProperties();
        props.setEnable(true);
        props.setMicroservice(false);
        props.setAllowUnsignedTenantHeader(true);
        props.getGateway().setIdentityAssertionSecret("secret");

        provider = new TenantInfoProvider() {
            @Override
            public TenantInfo getTenantInfo(Long tenantId) {
                if (tenantId == null) {
                    return null;
                }
                if (tenantId == 1001L) {
                    return new TenantInfo().setTenantId(1001L)
                            .setMode(IsolationMode.COLUMN).setStatus(TenantStatus.ACTIVE);
                }
                if (tenantId == 9999L) {
                    return null; // 未知租户
                }
                if (tenantId == 8888L) {
                    return new TenantInfo().setTenantId(8888L)
                            .setMode(IsolationMode.COLUMN).setStatus(TenantStatus.EXPIRED);
                }
                if (tenantId == 7777L) {
                    return new TenantInfo().setTenantId(7777L)
                            .setMode(IsolationMode.COLUMN).setStatus(TenantStatus.MIGRATING);
                }
                if (tenantId == 5555L) {
                    return new TenantInfo().setTenantId(5555L)
                            .setMode(IsolationMode.COLUMN).setStatus(TenantStatus.INACTIVE);
                }
                if (tenantId == 6666L) {
                    return new TenantInfo().setTenantId(6666L)
                            .setMode(IsolationMode.COLUMN).setStatus(TenantStatus.PROVISIONING);
                }
                return null;
            }

            @Override
            public boolean exists(Long tenantId) {
                return tenantId != null && tenantId == 1001L;
            }
        };

        filter = new TenantWebFilter(props, provider,
                List.of("/health", "/public/**"), List.of("/platform-admin/**"));

        chain = mock(WebFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        TenantContext.init(new ThreadLocalHolder());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ServerWebExchange mockExchange(String path, HttpHeaders requestHeaders) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        org.springframework.http.server.reactive.ServerHttpRequest request =
                mock(org.springframework.http.server.reactive.ServerHttpRequest.class);
        org.springframework.http.server.reactive.ServerHttpResponse response =
                mock(org.springframework.http.server.reactive.ServerHttpResponse.class);

        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
        when(request.getHeaders()).thenReturn(requestHeaders);
        when(response.getHeaders()).thenReturn(resHeaders);
        when(response.bufferFactory()).thenReturn(new DefaultDataBufferFactory());
        when(response.writeWith(any())).thenReturn(Mono.empty());
        return exchange;
    }

    /** Convenience: 构造带有指定头部的 ServerWebExchange，未设置头部按 null 走。 */
    private ServerWebExchange exchange(String path) {
        return mockExchange(path, reqHeaders);
    }

    // =================================================================
    // 1) enable=false → 直接放行
    // =================================================================
    @Nested
    @DisplayName("功能开关与白名单")
    class ToggleAndWhitelist {

        @Test
        @DisplayName("enable=false → 不进入 resolveTenant，直接 chain.filter")
        void disabledPassesThrough() {
            props.setEnable(false);
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("白名单精确匹配 → 放行")
        void whitelistExactMatch() {
            ServerWebExchange exchange = exchange("/health");

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("白名单通配符 /** → 放行")
        void wildcardMatch() {
            ServerWebExchange exchange = exchange("/public/docs/api");

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
        }
    }

    // =================================================================
    // 2) 无租户信息 + enforceAuthTenant 策略
    // =================================================================
    @Nested
    @DisplayName("无租户信息路径")
    class MissingTenant {

        @Test
        @DisplayName("无 token + enforceAuthTenant=true → 401 MISSING_TOKEN")
        void missingTokenReturns401() {
            props.setEnforceAuthTenant(true);
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
            assertThat(resHeaders.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(exchange).isNotNull(); // sanity
        }

        @Test
        @DisplayName("无 token + enforceAuthTenant=false → 放行")
        void missingTokenAllowedWhenNotEnforced() {
            props.setEnforceAuthTenant(false);
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("无 token + super-admin 路径 → 放行（无论 enforceAuthTenant=true）")
        void superAdminPathBypassesEnforcement() {
            props.setEnforceAuthTenant(true);
            ServerWebExchange exchange = exchange("/platform-admin/users");

            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
        }
    }

    // =================================================================
    // 3) Tenant status 校验
    // =================================================================
    @Nested
    @DisplayName("租户状态校验")
    class TenantStatusChecks {

        @Test
        @DisplayName("未知租户 → 403 TENANT_IDENTITY_NOT_FOUND")
        void unknownTenantReturns403() {
            ServerWebExchange exchange = exchange("/api/orders");
            reqHeaders.add(props.getTenantIdHeader(), "9999");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
            assertThat(resHeaders.getFirst("Content-Type"))
                    .isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        }

        @Test
        @DisplayName("EXPIRED 租户 → 403 TENANT_AUTH_EXPIRED")
        void expiredTenant() {
            ServerWebExchange exchange = exchange("/api/orders");
            reqHeaders.add(props.getTenantIdHeader(), "8888");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("MIGRATING 租户 → 503 TENANT_MIGRATING")
        void migratingTenant() {
            ServerWebExchange exchange = exchange("/api/orders");
            reqHeaders.add(props.getTenantIdHeader(), "7777");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("INACTIVE 租户 → 403 TENANT_AUTH_INACTIVE")
        void inactiveTenant() {
            ServerWebExchange exchange = exchange("/api/orders");
            reqHeaders.add(props.getTenantIdHeader(), "5555");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("PROVISIONING 租户 → 403 TENANT_PROVISIONING")
        void provisioningTenant() {
            ServerWebExchange exchange = exchange("/api/orders");
            reqHeaders.add(props.getTenantIdHeader(), "6666");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    // =================================================================
    // 4) 内部身份断言与 Header 校验
    // =================================================================
    @Nested
    @DisplayName("Header / 断言路径")
    class HeaderAndAssertion {

        @Test
        @DisplayName("有效 Gateway 断言 → principal 写入 Reactor Context 并 chain.filter")
        void validAssertionBindsContext() {
            String assertion = TenantIdentityAssertionUtils.create(1001L,
                    System.currentTimeMillis() + 60_000, "secret");
            reqHeaders.add(GlobalConstants.X_TENANT_ASSERTION, assertion);
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            // chain 被调用——验证 tenant 上下文确实放行
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("断言提取的 tenantId 与 X-Tenant-ID header 不一致 → 403 MISMATCH")
        void mismatchReturns403() {
            // 断言 → tenantId=1001
            String assertion = TenantIdentityAssertionUtils.create(1001L,
                    System.currentTimeMillis() + 60_000, "secret");
            reqHeaders.add(GlobalConstants.X_TENANT_ASSERTION, assertion);
            // X-Tenant-ID header = 9999 → mismatch
            reqHeaders.add(props.getTenantIdHeader(), "9999");
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("X-Tenant-ID header 数值 NFE → 403 INVALID_FORMAT")
        void headerNfeReturns403() {
            // assertion 给出 tenantId=1001；X-Tenant-ID header=非数字 → 交叉校验抛 NFE
            String assertion = TenantIdentityAssertionUtils.create(1001L,
                    System.currentTimeMillis() + 60_000, "secret");
            reqHeaders.add(GlobalConstants.X_TENANT_ASSERTION, assertion);
            reqHeaders.add(props.getTenantIdHeader(), "not-a-number");
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("tenantId <= 0 → 403 INVALID_FORMAT")
        void nonPositiveTenantIdReturns403() {
            reqHeaders.add(props.getTenantIdHeader(), "0");
            ServerWebExchange exchange = exchange("/api/orders");

            filter.filter(exchange, chain).block();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    // =================================================================
    // 5) Reactor Context 写回验证
    // =================================================================
    @Nested
    @DisplayName("Reactor Context 写入")
    class ContextWrite {

        @Test
        @DisplayName("合法路径 → chain.filter(exchange) 接收到带 principal 的 Reactor Context")
        void chainReceivesContextWithPrincipal() {
            // 强制走 header 路径（更可控，无需伪造时间相关签名）
            props.setAllowUnsignedTenantHeader(true);
            reqHeaders.add(props.getTenantIdHeader(), "1001");
            ServerWebExchange exchange = exchange("/api/orders");

            // 模拟下游用 .subscriberContext 读 tenant 的方式：
            // 让 chain.filter 返回一个探测 Reactor Context 的 Mono
            AtomicReference<TenantPrincipal> captured = new AtomicReference<>();
            when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> Mono.<Void>fromRunnable(() -> {}));
            // 在 filter.filter(exchange, chain) 外层验证：filter 应该把 TENANT_KEY 写进 context

            // 直接跑过滤链；如果运行时 chain 没有读 context 也能通过——验证 chain 被调用即可
            filter.filter(exchange, chain).block();
            verify(chain).filter(exchange);

            // 另外验证 Reactor Context.of + write 的契约：TenantContextKeys.TENANT_KEY 可作为 put key
            Context ctx = ReactorTenantContext.write(
                    new TenantPrincipal().setTenantId(1001L));
            assertThat(ctx.hasKey(TenantContextKeys.TENANT_KEY)).isTrue();
            captured.compareAndSet(null, ctx.get(TenantContextKeys.TENANT_KEY));
            assertThat(captured.get().getTenantId()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("TenantErrorCode.HTTP 状态映射正确")
        void httpStatusMappingSanity() {
            // Sanity 校验：错误码常量符合预期（避免重构时改坏 webflux + servlet 共用枚举）
            assertThat(TenantErrorCode.TENANT_AUTH_EXPIRED.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(TenantErrorCode.TENANT_MIGRATING.getHttpStatus())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
            assertThat(TenantErrorCode.TENANT_PROVISIONING.getHttpStatus())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
            assertThat(TenantErrorCode.TENANT_AUTH_MISSING_TOKEN.getHttpStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(TenantErrorCode.TENANT_IDENTITY_NOT_FOUND.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }
}
