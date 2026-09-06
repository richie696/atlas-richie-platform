/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.exceptions.VectorCapabilityMismatchException;
import cn.richie696.component.vector.exceptions.VectorStoreNotExistException;
import cn.richie696.component.vector.model.IndexInfo;
import cn.richie696.component.vector.service.VectorIndexStatsOperations;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.topology.DefaultVectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import cn.richie696.component.vector.topology.VectorStoreStartupFailure;
import cn.richie696.component.vector.topology.VectorStoreStartupPhase;
import cn.richie696.component.vector.topology.VectorTopologyDefinitions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultVectorStoreDiagnosticsTest {

    @Test
    void shouldProbeStoresIndependentlyAndAggregateDegraded() {
        VectorStoreHandle healthy = handle("documents", VectorProvider.MILVUS, healthReturning(true));
        VectorStoreHandle unhealthy = handle("prompts", VectorProvider.POSTGRESQL, healthReturning(false));
        DefaultVectorStoreDiagnostics diagnostics = diagnostics(List.of(healthy, unhealthy), List.of(), definitions(
                healthy.definition(), unhealthy.definition()));

        VectorHealthReport report = diagnostics.checkAll();

        assertThat(report.status()).isEqualTo(VectorStoreHealthStatus.DEGRADED);
        assertThat(report.stores()).extracting(VectorStoreHealthSnapshot::storeId)
                .containsExactly(VectorStoreId.of("documents"), VectorStoreId.of("prompts"));
        assertThat(report.stores().get(0).status()).isEqualTo(VectorStoreHealthStatus.UP);
        assertThat(report.stores().get(1).status()).isEqualTo(VectorStoreHealthStatus.DOWN);
        assertThat(report.stores().get(1).errorCategory()).isEqualTo(VectorErrorCategory.HEALTH_CHECK_FAILED);
    }

    @Test
    void shouldClassifyThrownProbeWithoutExposingProviderMessage() {
        VectorStoreHandle handle = handle("documents", VectorProvider.MILVUS, newStats(() -> {
            throw new IllegalStateException("secret-provider-host:19530");
        }));

        VectorStoreHealthSnapshot snapshot = diagnostics(
                List.of(handle), List.of(), definitions(handle.definition())).check(handle.id());

        assertThat(snapshot.status()).isEqualTo(VectorStoreHealthStatus.DOWN);
        assertThat(snapshot.errorCategory()).isEqualTo(VectorErrorCategory.PROVIDER_UNAVAILABLE);
        assertThat(snapshot.toString()).doesNotContain("secret-provider-host");
    }

    @Test
    void shouldReturnUnknownWithoutHealthCapability() {
        VectorStoreHandle handle = handle("documents", VectorProvider.WEAVIATE, null);

        VectorStoreHealthSnapshot snapshot = diagnostics(
                List.of(handle), List.of(), definitions(handle.definition())).check(handle.id());

        assertThat(snapshot.status()).isEqualTo(VectorStoreHealthStatus.UNKNOWN);
        assertThat(snapshot.checkedIndexCount()).isZero();
        assertThat(snapshot.errorCategory()).isEqualTo(VectorErrorCategory.NONE);
    }

    @Test
    void shouldIncludeOptionalStoreStartupFailureWithoutFailingHealthyStore() {
        VectorStoreHandle healthy = handle("documents", VectorProvider.MILVUS, healthReturning(true));
        VectorStoreDefinition optional = definition("prompts", false);
        VectorStoreStartupFailure failure = new VectorStoreStartupFailure(
                optional.id(), VectorStoreStartupPhase.STORE_CREATION, "java.net.ConnectException");

        VectorHealthReport report = diagnostics(
                List.of(healthy), List.of(failure), definitions(healthy.definition(), optional)).checkAll();

        assertThat(report.status()).isEqualTo(VectorStoreHealthStatus.DEGRADED);
        assertThat(report.stores()).hasSize(2);
        assertThat(report.stores().get(1).storeId()).isEqualTo(optional.id());
        assertThat(report.stores().get(1).errorCategory()).isEqualTo(VectorErrorCategory.STARTUP_FAILURE);
        assertThat(report.stores().get(1).required()).isFalse();
        assertThat(report.toString()).doesNotContain("ConnectException");
    }

    @Test
    void shouldExposeStableRouteAndCapabilityCategories() {
        VectorStoreHandle handle = handle("documents", VectorProvider.MILVUS, null);
        DefaultVectorServiceRegistry registry = new DefaultVectorServiceRegistry(List.of(handle));

        assertThatThrownBy(() -> registry.require(VectorStoreId.of("missing")))
                .isInstanceOfSatisfying(VectorStoreNotExistException.class,
                        error -> assertThat(error.errorCategory()).isEqualTo(VectorErrorCategory.ROUTE_NOT_FOUND));
        assertThatThrownBy(() -> handle.requireCapability(VectorIndexStatsOperations.class))
                .isInstanceOfSatisfying(VectorCapabilityMismatchException.class,
                        error -> assertThat(error.errorCategory()).isEqualTo(VectorErrorCategory.CAPABILITY_MISMATCH));
    }

    private static DefaultVectorStoreDiagnostics diagnostics(
            List<VectorStoreHandle> handles,
            List<VectorStoreStartupFailure> failures,
            VectorTopologyDefinitions definitions) {
        return new DefaultVectorStoreDiagnostics(new DefaultVectorServiceRegistry(handles), failures, definitions);
    }

    private static VectorStoreHandle handle(
            String storeId,
            VectorProvider provider,
            VectorIndexStatsOperations stats) {
        VectorStoreDefinition definition = definition(storeId, true);
        VectorStoreHandle.Builder builder = VectorStoreHandle.builder(definition, provider, proxy(VectorService.class))
                .storeCapabilities(VectorStoreCapabilities.none());
        if (stats != null) {
            builder.capability(VectorIndexStatsOperations.class, stats);
        }
        return builder.build();
    }

    private static VectorStoreDefinition definition(String storeId, boolean required) {
        return new VectorStoreDefinition(
                VectorStoreId.of(storeId),
                VectorConnectionId.of("connection-" + storeId),
                "aiEmbeddingModel",
                "default",
                required,
                Set.of());
    }

    private static VectorTopologyDefinitions definitions(VectorStoreDefinition... stores) {
        List<VectorConnectionDefinition> connections = java.util.Arrays.stream(stores)
                .map(store -> new VectorConnectionDefinition(
                        store.connectionId(),
                        store.id().value().equals("prompts") ? VectorProvider.POSTGRESQL : VectorProvider.MILVUS,
                        Map.of()))
                .toList();
        return new VectorTopologyDefinitions(connections, List.of(stores));
    }

    private static VectorIndexStatsOperations healthReturning(boolean value) {
        return newStats(() -> value);
    }

    private static VectorIndexStatsOperations newStats(HealthProbe probe) {
        return new VectorIndexStatsOperations() {
            @Override
            public long countDocuments(String indexName) {
                return 0;
            }

            @Override
            public List<IndexInfo> listIndexes() {
                return List.of();
            }

            @Override
            public IndexInfo getIndexStats(String indexName) {
                return null;
            }

            @Override
            public boolean healthCheck(String indexName) {
                return probe.check();
            }
        };
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                }));
    }

    @FunctionalInterface
    private interface HealthProbe {
        boolean check();
    }
}
