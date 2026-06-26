package com.richie.component.tenant.monitor;

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.spi.CachingTenantInfoProvider;
import com.richie.component.tenant.spi.TenantInfoProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DisplayName("TenantMeterBinder — 多租户监控指标注册")
class TenantMeterBinderTest {

    private MeterRegistry meterRegistry;
    private TenantMetricsCollector metricsCollector;
    private DataSourceCircuitBreaker circuitBreaker;
    private MultiTenancyProperties properties;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new TenantMetricsCollector();
        properties = new MultiTenancyProperties();
        properties.getCircuit().setFailureThreshold(3);
        properties.getCircuit().setOpenWindowMs(60000);
        circuitBreaker = new DataSourceCircuitBreaker(properties);
    }

    @Test
    @DisplayName("无缓存 Provider 时所有指标仍正常注册")
    void binderRegistersMetricsWithoutCache() {
        TenantMeterBinder binder = new TenantMeterBinder(
            circuitBreaker, metricsCollector, null);
        binder.bindTo(meterRegistry);

        assertThat(meterRegistry.getMeters())
            .as("至少注册 circuit + sql 相关指标")
            .hasSizeGreaterThanOrEqualTo(4);
    }

    @Nested
    @DisplayName("熔断器指标")
    class CircuitBreakerMetrics {

        @Test
        @DisplayName("circuit.status 为未注册数据源返回 CLOSED(0)")
        void unknownKeyStatusIsClosed() {
            circuitBreaker.recordFailure("ds-1");
            circuitBreaker.recordFailure("ds-1");

            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, null);
            binder.bindTo(meterRegistry);

            Gauge gauge = meterRegistry.get("tenant.circuit.status")
                .tag("datasource", "ds-1").gauge();
            assertThat(gauge.value()).isZero(); // CLOSED (未达阈值)

            circuitBreaker.recordFailure("ds-1"); // 达到阈值3
            // gauge 是 AtomicReference 实时查询,应已变为 OPEN
            assertThat(gauge.value()).isEqualTo(1.0); // OPEN
        }

        @Test
        @DisplayName("circuit.failures 记录失败次数")
        void failuresGaugeTracksCount() {
            circuitBreaker.recordFailure("ds-1");
            circuitBreaker.recordFailure("ds-1");

            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, null);
            binder.bindTo(meterRegistry);

            Gauge gauge = meterRegistry.get("tenant.circuit.failures")
                .tag("datasource", "ds-1").gauge();
            assertThat(gauge.value()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("同一 breaker 监控多个数据源")
        void multipleDataSources() {
            circuitBreaker.recordFailure("ds-1");
            circuitBreaker.recordFailure("ds-2");
            circuitBreaker.recordFailure("ds-2");
            circuitBreaker.recordFailure("ds-2"); // OPEN

            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, null);
            binder.bindTo(meterRegistry);

            Gauge status1 = meterRegistry.get("tenant.circuit.status")
                .tag("datasource", "ds-1").gauge();
            Gauge status2 = meterRegistry.get("tenant.circuit.status")
                .tag("datasource", "ds-2").gauge();
            assertThat(status1.value()).isZero();  // CLOSED
            assertThat(status2.value()).isEqualTo(1.0); // OPEN

            Gauge failures2 = meterRegistry.get("tenant.circuit.failures")
                .tag("datasource", "ds-2").gauge();
            assertThat(failures2.value()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("SQL 改写指标")
    class SqlRewriteMetrics {

        @Test
        @DisplayName("sql.rewrite.attempts 注册为 Gauge (AtomicLong-backed)")
        void attemptsRegistered() {
            metricsCollector.incrementLineRewriteAttempts();
            metricsCollector.incrementLineRewriteAttempts();
            metricsCollector.incrementTableRewriteAttempts();

            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, null);
            binder.bindTo(meterRegistry);

            double lineAttempts = meterRegistry.get("tenant.sql.rewrite.attempts")
                .tag("type", "line").gauge().value();
            double tableAttempts = meterRegistry.get("tenant.sql.rewrite.attempts")
                .tag("type", "table").gauge().value();

            assertThat(lineAttempts).isEqualTo(2.0);
            assertThat(tableAttempts).isEqualTo(1.0);
        }

        @Test
        @DisplayName("sql.rewrite.success 注册为 Gauge (AtomicLong-backed)")
        void successRegistered() {
            metricsCollector.incrementLineRewriteSuccess();
            metricsCollector.incrementTableRewriteSuccess();
            metricsCollector.incrementTableRewriteSuccess();

            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, null);
            binder.bindTo(meterRegistry);

            double lineSuccess = meterRegistry.get("tenant.sql.rewrite.success")
                .tag("type", "line").gauge().value();
            double tableSuccess = meterRegistry.get("tenant.sql.rewrite.success")
                .tag("type", "table").gauge().value();

            assertThat(lineSuccess).isEqualTo(1.0);
            assertThat(tableSuccess).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("缓存指标")
    class CacheMetrics {

        @Test
        @DisplayName("缓存指标在无缓存 Provider 时自动跳过")
        void noCacheProviderSkipsCacheMetrics() {
            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, null);
            binder.bindTo(meterRegistry);

            // cache metrics should NOT exist
            List<Meter> sizeMeters = meterRegistry.getMeters().stream()
                .filter(m -> "tenant.cache.size".equals(m.getId().getName()))
                .toList();
            assertThat(sizeMeters).isEmpty();
        }

        @Test
        @DisplayName("有缓存 Provider 时注册 cache.size/hits/misses")
        void withCacheProvider() {
            metricsCollector.incrementCacheHits();
            metricsCollector.incrementCacheHits();
            metricsCollector.incrementCacheHits();
            metricsCollector.incrementCacheMisses();

            TenantInfoProvider delegate = Mockito.mock(TenantInfoProvider.class);
            when(delegate.getTenantInfo(anyLong())).thenReturn(null);
            CachingTenantInfoProvider cache =
                new CachingTenantInfoProvider(delegate, 60, 100, metricsCollector);

            @SuppressWarnings("unchecked")
            ObjectProvider<CachingTenantInfoProvider> cacheProvider = Mockito.mock(ObjectProvider.class);
            when(cacheProvider.getIfUnique()).thenReturn(cache);

            TenantMeterBinder binder = new TenantMeterBinder(
                circuitBreaker, metricsCollector, cacheProvider);
            binder.bindTo(meterRegistry);

            Gauge sizeGauge = meterRegistry.get("tenant.cache.size").gauge();
            Gauge ratioGauge = meterRegistry.get("tenant.cache.hit.ratio").gauge();
            assertThat(sizeGauge.value()).isZero(); // empty cache
            assertThat(ratioGauge.value()).isEqualTo(0.75); // 3/4

            double hits = meterRegistry.get("tenant.cache.hits").gauge().value();
            double misses = meterRegistry.get("tenant.cache.misses").gauge().value();
            assertThat(hits).isEqualTo(3.0);
            assertThat(misses).isEqualTo(1.0);
        }
    }
}
