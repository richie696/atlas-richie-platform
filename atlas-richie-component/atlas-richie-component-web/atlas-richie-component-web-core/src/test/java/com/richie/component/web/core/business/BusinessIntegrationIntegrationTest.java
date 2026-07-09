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
package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务能力集成集成测试：3 个拦截器链式工作（README.md §4.9）。
 */
class BusinessIntegrationIntegrationTest {

    private final IdempotencyCache cache = new IdempotencyCache(60);

    private final BusinessIntegrationProperties.Tenant tenantConfig = new BusinessIntegrationProperties.Tenant();
    private final BusinessIntegrationProperties.Idempotency idempotencyConfig = new BusinessIntegrationProperties.Idempotency();
    private final BusinessIntegrationProperties.ApiVersion versionConfig = new BusinessIntegrationProperties.ApiVersion();

    private final TenantInterceptor tenant = new TenantInterceptor(tenantConfig);
    private final IdempotencyInterceptor idempotency = new IdempotencyInterceptor(idempotencyConfig, cache);
    private final ApiVersionInterceptor version = new ApiVersionInterceptor(versionConfig);

    @Test
    void fullChain_attributeAndContextFlow() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Tenant-Id", "tenant-99")
                .header("X-Idempotency-Key", "order-abc-001")
                .header("X-Api-Version", "v2")
                .build();

        List<com.richie.component.web.core.spi.WebInterceptor> chain = List.of(tenant, idempotency, version);
        new DefaultWebInterceptorChain(chain).proceed(ctx);

        assertThat((String) ctx.attribute(TenantInterceptor.TENANT_ATTRIBUTE)).isEqualTo("tenant-99");
        assertThat((String) ctx.attribute("apiVersion")).isEqualTo("v2");
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void idempotencyReplay_denyShortCircuitsEvenIfTenantAndVersionWouldSucceed() throws Exception {
        // 第一次预热
        MutableWebRequestContext first = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Tenant-Id", "tenant-1")
                .header("X-Idempotency-Key", "replay-1")
                .header("X-Api-Version", "v1")
                .build();
        new DefaultWebInterceptorChain(List.of(tenant, idempotency, version)).proceed(first);

        // 第二次同幂等 key → 短路
        MutableWebRequestContext second = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Tenant-Id", "tenant-1")
                .header("X-Idempotency-Key", "replay-1")
                .header("X-Api-Version", "v1")
                .build();
        new DefaultWebInterceptorChain(List.of(tenant, idempotency, version)).proceed(second);
        assertThat(second.isShortCircuited()).isTrue();
        assertThat(second.responseStatus()).isEqualTo(409);
    }

    @Test
    void noHeaders_passesThroughWithDefaults() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/health")
                .build();
        new DefaultWebInterceptorChain(List.of(tenant, idempotency, version)).proceed(ctx);
        assertThat((String) ctx.attribute(TenantInterceptor.TENANT_ATTRIBUTE)).isNull();
        assertThat((String) ctx.attribute("apiVersion")).isEqualTo("default");
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void orderChain_tenantBeforeIdempotencyBeforeVersion() {
        assertThat(tenant.getOrder()).isLessThan(idempotency.getOrder());
        assertThat(idempotency.getOrder()).isLessThan(version.getOrder());
        assertThat(tenant.getOrder()).isEqualTo(260);
        assertThat(idempotency.getOrder()).isEqualTo(270);
        assertThat(version.getOrder()).isEqualTo(280);
    }
}