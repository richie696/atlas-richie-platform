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
package com.richie.component.vector.service;

import com.richie.component.vector.config.VectorFacadeProperties;
import com.richie.component.vector.operations.VectorOperationsFacade;
import com.richie.component.vector.operations.VectorOperationsFacade.VectorFacadeExecutionException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VectorOperationsFacade} 单元测试。
 * <p>
 * 覆盖：主 provider 成功、主 provider 重试成功、回退链、全部失败聚合异常、指标记录与无 NPE、
 * provider 列表与未知 provider 错误信息。
 */
class VectorOperationsFacadeTest {

    private static final String PRIMARY = "redisVectorServiceImpl";
    private static final String FALLBACK = "milvusVectorServiceImpl";

    private Map<String, VectorService> providers;
    private VectorFacadeProperties props;
    private VectorService primaryMock;
    private VectorService fallbackMock;

    @BeforeEach
    void setUp() {
        primaryMock = mock(VectorService.class);
        fallbackMock = mock(VectorService.class);

        providers = new LinkedHashMap<>();
        providers.put(PRIMARY, primaryMock);
        providers.put(FALLBACK, fallbackMock);

        props = new VectorFacadeProperties();
        props.setDefaultProvider(PRIMARY);
        props.setFallbackChain(new java.util.ArrayList<>(List.of(FALLBACK)));
        props.setMaxRetries(2);
        // 退避设为 1ms，测试运行更快；逻辑与 100ms 完全等价
        props.setRetryBackoffMillis(1L);
    }

    private VectorOperationsFacade newFacade(ObjectProvider<MeterRegistry> registryProvider) {
        return new VectorOperationsFacade(providers, props, registryProvider);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<MeterRegistry> registryProvider(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    // ==================== Test 1 ====================

    @Test
    @DisplayName("execute: 主 provider 成功 — 直接返回结果，不触发回退")
    void execute_withPrimarySucceeds_returnsResult() {
        VectorOperationsFacade facade = newFacade(registryProvider(null));
        when(primaryMock.countDocuments(eq("idx"))).thenReturn(42L);

        Long count = facade.execute("countDocuments", svc -> svc.countDocuments("idx"));

        assertThat(count).isEqualTo(42L);
        verify(primaryMock, times(1)).countDocuments("idx");
        verify(fallbackMock, never()).countDocuments(anyString());
    }

    // ==================== Test 2 ====================

    @Test
    @DisplayName("execute: 主 provider 前 2 次失败，第 3 次成功 — 返回结果")
    void execute_withPrimaryFails_retriesAndSucceeds() {
        VectorOperationsFacade facade = newFacade(registryProvider(null));
        AtomicInteger calls = new AtomicInteger();
        when(primaryMock.countDocuments(eq("idx"))).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            if (n < 3) {
                throw new RuntimeException("primary-down-" + n);
            }
            return 7L;
        });

        Long count = facade.execute("countDocuments", svc -> svc.countDocuments("idx"));

        assertThat(count).isEqualTo(7L);
        assertThat(calls.get()).isEqualTo(3);
        verify(fallbackMock, never()).countDocuments(anyString());
    }

    // ==================== Test 3 ====================

    @Test
    @DisplayName("execute: 主 provider 全部重试失败，回退链 provider 成功")
    void execute_withPrimaryFailsAllRetries_fallsBackToChain() {
        VectorOperationsFacade facade = newFacade(registryProvider(null));
        when(primaryMock.countDocuments(eq("idx")))
                .thenThrow(new RuntimeException("primary-permanent-failure"));
        when(fallbackMock.countDocuments(eq("idx"))).thenReturn(99L);

        Long count = facade.execute("countDocuments", svc -> svc.countDocuments("idx"));

        assertThat(count).isEqualTo(99L);
        // 主 provider: maxRetries=2 ⇒ 总尝试 3 次
        verify(primaryMock, times(3)).countDocuments("idx");
        // 回退 provider: 首次即成功
        verify(fallbackMock, times(1)).countDocuments("idx");
    }

    // ==================== Test 4 ====================

    @Test
    @DisplayName("execute: 所有 provider 均失败 — 抛出 VectorFacadeExecutionException，包含每个 provider 的最后错误")
    void execute_allProvidersFail_throwsAggregatedException() {
        VectorOperationsFacade facade = newFacade(registryProvider(null));
        when(primaryMock.countDocuments(eq("idx")))
                .thenThrow(new RuntimeException("primary-err"));
        when(fallbackMock.countDocuments(eq("idx")))
                .thenThrow(new IllegalStateException("fallback-err"));

        assertThatThrownBy(() -> facade.execute("countDocuments", svc -> svc.countDocuments("idx")))
                .isInstanceOfSatisfying(VectorFacadeExecutionException.class, ex -> {
                    List<VectorOperationsFacade.ProviderFailure> failures = ex.getFailures();
                    assertThat(failures).hasSize(2);
                    assertThat(failures).extracting(VectorOperationsFacade.ProviderFailure::provider)
                            .containsExactly(PRIMARY, FALLBACK);
                    assertThat(failures.get(0).cause()).hasMessageContaining("primary-err");
                    assertThat(failures.get(1).cause()).hasMessageContaining("fallback-err");
                });

        // 主 provider 3 次 + 回退 3 次
        verify(primaryMock, times(3)).countDocuments("idx");
        verify(fallbackMock, times(3)).countDocuments("idx");
    }

    // ==================== Test 5 ====================

    @Test
    @DisplayName("execute: 注入 MeterRegistry 时记录 Timer 与失败 Counter")
    void execute_recordsTimerWhenMeterRegistryPresent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VectorOperationsFacade facade = newFacade(registryProvider(registry));

        when(primaryMock.countDocuments(eq("idx"))).thenReturn(1L);

        facade.execute("countDocuments", svc -> svc.countDocuments("idx"));

        Timer timer = registry.find("vector.facade.operation")
                .tags("provider", PRIMARY, "operation", "countDocuments")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isPositive();

        // 成功路径下不应记录 failure 计数器
        Double failureCount = registry.find("vector.facade.failure")
                .tags("provider", PRIMARY, "operation", "countDocuments")
                .counter() == null
                ? 0.0
                : registry.find("vector.facade.failure")
                        .tags("provider", PRIMARY, "operation", "countDocuments")
                        .counter().count();
        assertThat(failureCount).isEqualTo(0.0);
    }

    // ==================== Test 6 ====================

    @Test
    @DisplayName("execute: MeterRegistry 缺失时不抛 NPE，正常返回结果")
    void execute_skipsMetricsWhenMeterRegistryAbsent() {
        VectorOperationsFacade facade = newFacade(registryProvider(null));
        when(primaryMock.countDocuments(eq("idx"))).thenReturn(5L);

        Long count = facade.execute("countDocuments", svc -> svc.countDocuments("idx"));

        assertThat(count).isEqualTo(5L);
        verify(primaryMock, times(1)).countDocuments("idx");
    }

    // ==================== Test 7 ====================

    @Test
    @DisplayName("providerNames: 返回所有已注册的 VectorService bean name (8 个)")
    void providerNames_returnsAllRegisteredBeans() {
        Map<String, VectorService> eightProviders = new HashMap<>();
        String[] names = {
                "redisVectorServiceImpl", "milvusVectorServiceImpl", "mongodbAtlasVectorServiceImpl",
                "postgresqlVectorServiceImpl", "qdrantVectorServiceImpl", "neo4jVectorServiceImpl",
                "elasticsearchVectorServiceImpl", "weaviateVectorServiceImpl"
        };
        for (String name : names) {
            eightProviders.put(name, mock(VectorService.class));
        }

        VectorOperationsFacade facade = new VectorOperationsFacade(
                eightProviders, props, registryProvider(null));

        assertThat(facade.providerNames()).hasSize(8);
        assertThat(facade.providerNames()).containsExactlyInAnyOrder(names);
    }

    // ==================== Test 8 ====================

    @Test
    @DisplayName("get: 未知 provider 名抛 IllegalArgumentException，错误消息包含已注册列表")
    void get_unknownProvider_throwsIllegalArgument() {
        VectorOperationsFacade facade = newFacade(registryProvider(null));

        assertThatThrownBy(() -> facade.get("ghostVectorService"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghostVectorService")
                .hasMessageContaining(PRIMARY)
                .hasMessageContaining(FALLBACK);

        // 主 provider 本身应能正常获取
        assertThat(facade.get(PRIMARY)).isSameAs(primaryMock);
    }
}
