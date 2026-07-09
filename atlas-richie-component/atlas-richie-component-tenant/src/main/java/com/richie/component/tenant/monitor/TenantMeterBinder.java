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
package com.richie.component.tenant.monitor;

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.circuit.DataSourceCircuitBreaker.CircuitStatus;
import com.richie.component.tenant.spi.CachingTenantInfoProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

/**
 * 多租户组件内建 Micrometer {@link MeterBinder}。
 *
 * <p>自动注册以下指标（{@code @ConditionalOnClass(MeterRegistry.class)} 激活）：</p>
 *
 * <h2>熔断器指标</h2>
 * <ul>
 *   <li>{@code tenant.circuit.status} — Tag {@code datasource=shared|<tenantId>}，
 *       0=CLOSED, 1=OPEN, 2=HALF_OPEN</li>
 *   <li>{@code tenant.circuit.failures} — Tag {@code datasource}，当前连续失败次数</li>
 * </ul>
 *
 * <h2>SQL 改写指标</h2>
 * <ul>
 *   <li>{@code tenant.sql.rewrite.attempts} — Tag {@code type=line|table}，改写尝试总次数</li>
 *   <li>{@code tenant.sql.rewrite.success} — Tag {@code type=line|table}，改写成功总次数</li>
 * </ul>
 *
 * <h2>租户缓存指标</h2>
 * <ul>
 *   <li>{@code tenant.cache.size} — {@link CachingTenantInfoProvider} 当前缓存条目数</li>
 *   <li>{@code tenant.cache.hits} — 缓存命中总次数</li>
 *   <li>{@code tenant.cache.misses} — 缓存未命中总次数</li>
 *   <li>{@code tenant.cache.hit.ratio} — 缓存命中率（0.0 ~ 1.0）</li>
 * </ul>
 *
 * <p>指标命名风格采用 {@code tenant.<domain>.<metric>}，与项目现有
 * {@code redis.stream.*} / {@code grpc.server.*} 一致。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class TenantMeterBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(TenantMeterBinder.class);

    private final DataSourceCircuitBreaker circuitBreaker;
    private final TenantMetricsCollector metricsCollector;
    private final ObjectProvider<CachingTenantInfoProvider> cachingProvider;

    public TenantMeterBinder(DataSourceCircuitBreaker circuitBreaker,
                             TenantMetricsCollector metricsCollector,
                             ObjectProvider<CachingTenantInfoProvider> cachingProvider) {
        this.circuitBreaker = circuitBreaker;
        this.metricsCollector = metricsCollector;
        this.cachingProvider = cachingProvider;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (metricsCollector == null) {
            log.warn("TenantMetricsCollector not available — monitoring disabled (multi-tenancy.monitor.enabled=false), skipping metric registration");
            return;
        }

        // ==================== 熔断器指标 ====================

        // tenant.circuit.status: 每个数据源的熔断状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）
        // 使用 Live lookup，每次采样时从 circuitBreaker 读取最新状态
        circuitBreaker.getAllStatuses().forEach((key, snapshot) -> {
            List<Tag> tags = List.of(Tag.of("datasource", key));
            Gauge.builder("tenant.circuit.status", circuitBreaker, cb -> {
                CircuitStatus st = cb.getStatus(key);
                return switch (st) {
                    case CLOSED -> 0;
                    case OPEN -> 1;
                    case HALF_OPEN -> 2;
                };
            })
                .tags(tags)
                .description("Circuit breaker status: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .register(registry);

            Gauge.builder("tenant.circuit.failures", circuitBreaker, cb -> {
                    var all = cb.getAllStatuses();
                    var snap = all.get(key);
                    return snap != null ? (double) snap.failures() : 0.0;
                })
                .tags(tags)
                .description("Current consecutive failure count")
                .register(registry);
        });

        // ==================== SQL 改写指标 ====================

        Gauge.builder("tenant.sql.rewrite.attempts", metricsCollector, TenantMetricsCollector::getLineRewriteAttempts)
            .tag("type", "line")
            .description("Column mode SQL rewrite attempts")
            .register(registry);

        Gauge.builder("tenant.sql.rewrite.success", metricsCollector, TenantMetricsCollector::getLineRewriteSuccess)
            .tag("type", "line")
            .description("Column mode SQL rewrite success count")
            .register(registry);

        Gauge.builder("tenant.sql.rewrite.attempts", metricsCollector, TenantMetricsCollector::getTableRewriteAttempts)
            .tag("type", "table")
            .description("Table mode SQL rewrite attempts")
            .register(registry);

        Gauge.builder("tenant.sql.rewrite.success", metricsCollector, TenantMetricsCollector::getTableRewriteSuccess)
            .tag("type", "table")
            .description("Table mode SQL rewrite success count")
            .register(registry);

        // ==================== 租户缓存指标 ====================

        // 仅当 CachingTenantInfoProvider 可用时注册缓存指标
        CachingTenantInfoProvider cache = cachingProvider != null ? cachingProvider.getIfUnique() : null;
        if (cache != null) {
            Gauge.builder("tenant.cache.size", cache, CachingTenantInfoProvider::size)
                .description("Tenant info cache entry count")
                .register(registry);

            Gauge.builder("tenant.cache.hits", metricsCollector, TenantMetricsCollector::getCacheHits)
                .description("Tenant info cache hit count")
                .register(registry);

            Gauge.builder("tenant.cache.misses", metricsCollector, TenantMetricsCollector::getCacheMisses)
                .description("Tenant info cache miss count")
                .register(registry);

            Gauge.builder("tenant.cache.hit.ratio", metricsCollector, TenantMetricsCollector::getCacheHitRatio)
                .description("Tenant info cache hit ratio (0.0 ~ 1.0)")
                .register(registry);
        }

        log.info("TenantMeterBinder registered: circuit, sql.rewrite, cache metrics");
    }
}
