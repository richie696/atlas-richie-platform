/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.service.VectorIndexStatsOperations;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import cn.richie696.component.vector.topology.VectorStoreStartupFailure;
import cn.richie696.component.vector.topology.VectorTopologyDefinitions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Default pull-based implementation that isolates each Store health probe. */
public final class DefaultVectorStoreDiagnostics implements VectorStoreDiagnostics {

    private final VectorServiceRegistry registry;
    private final List<VectorStoreStartupFailure> startupFailures;
    private final Map<VectorStoreId, VectorProvider> configuredProviders;
    private final Map<VectorStoreId, Boolean> configuredRequired;

    public DefaultVectorStoreDiagnostics(
            VectorServiceRegistry registry,
            List<VectorStoreStartupFailure> startupFailures,
            VectorTopologyDefinitions definitions) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.startupFailures = List.copyOf(startupFailures == null ? List.of() : startupFailures);
        VectorTopologyDefinitions safeDefinitions = definitions == null
                ? new VectorTopologyDefinitions(List.of(), List.of())
                : definitions;
        Map<cn.richie696.component.vector.topology.VectorConnectionId, VectorProvider> providersByConnection =
                new LinkedHashMap<>();
        for (VectorConnectionDefinition connection : safeDefinitions.connections()) {
            providersByConnection.put(connection.id(), connection.provider());
        }
        Map<VectorStoreId, VectorProvider> providers = new LinkedHashMap<>();
        Map<VectorStoreId, Boolean> required = new LinkedHashMap<>();
        safeDefinitions.stores().forEach(store -> {
            VectorProvider provider = providersByConnection.get(store.connectionId());
            if (provider != null) {
                providers.put(store.id(), provider);
            }
            required.put(store.id(), store.required());
        });
        registry.describeStores().forEach(store -> {
            providers.put(store.id(), store.provider());
            required.put(store.id(), store.required());
        });
        this.configuredProviders = Map.copyOf(providers);
        this.configuredRequired = Map.copyOf(required);
    }

    @Override
    public VectorStoreHealthSnapshot check(VectorStoreId storeId) {
        return checkHandle(registry.require(storeId));
    }

    @Override
    public VectorHealthReport checkAll() {
        List<VectorStoreHealthSnapshot> snapshots = new ArrayList<>();
        Set<VectorStoreId> visited = new LinkedHashSet<>();
        registry.describeStores().forEach(descriptor -> {
            snapshots.add(checkHandle(registry.require(descriptor.id())));
            visited.add(descriptor.id());
        });
        startupFailures.stream()
                .filter(failure -> visited.add(failure.storeId()))
                .forEach(failure -> snapshots.add(new VectorStoreHealthSnapshot(
                        failure.storeId(),
                        requireConfiguredProvider(failure.storeId()),
                        configuredRequired.getOrDefault(failure.storeId(), false),
                        VectorStoreHealthStatus.DOWN,
                        VectorErrorCategory.STARTUP_FAILURE,
                        0)));
        return new VectorHealthReport(aggregate(snapshots), snapshots);
    }

    private VectorStoreHealthSnapshot checkHandle(VectorStoreHandle handle) {
        var health = handle.capability(VectorIndexStatsOperations.class);
        if (health.isEmpty()) {
            return snapshot(handle, VectorStoreHealthStatus.UNKNOWN, VectorErrorCategory.NONE, 0);
        }
        List<String> logicalIndexes = handle.definition().indexes().isEmpty()
                ? List.of(handle.definition().defaultIndex())
                : List.copyOf(handle.definition().indexes().keySet());
        int checked = 0;
        try {
            for (String logicalIndex : logicalIndexes) {
                checked++;
                if (!health.orElseThrow().healthCheck(logicalIndex)) {
                    return snapshot(handle, VectorStoreHealthStatus.DOWN,
                            VectorErrorCategory.HEALTH_CHECK_FAILED, checked);
                }
            }
            return snapshot(handle, VectorStoreHealthStatus.UP, VectorErrorCategory.NONE, checked);
        } catch (RuntimeException exception) {
            return snapshot(handle, VectorStoreHealthStatus.DOWN,
                    VectorErrorCategory.PROVIDER_UNAVAILABLE, checked);
        }
    }

    private static VectorStoreHealthSnapshot snapshot(
            VectorStoreHandle handle,
            VectorStoreHealthStatus status,
            VectorErrorCategory errorCategory,
            int checked) {
        return new VectorStoreHealthSnapshot(
                handle.id(), handle.provider(), handle.definition().required(), status, errorCategory, checked);
    }

    private VectorProvider requireConfiguredProvider(VectorStoreId storeId) {
        VectorProvider provider = configuredProviders.get(storeId);
        if (provider == null) {
            throw new IllegalStateException("missing provider identity for degraded vector store: " + storeId);
        }
        return provider;
    }

    private static VectorStoreHealthStatus aggregate(List<VectorStoreHealthSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return VectorStoreHealthStatus.UNKNOWN;
        }
        long up = snapshots.stream().filter(item -> item.status() == VectorStoreHealthStatus.UP).count();
        long down = snapshots.stream().filter(item -> item.status() == VectorStoreHealthStatus.DOWN).count();
        long unknown = snapshots.stream().filter(item -> item.status() == VectorStoreHealthStatus.UNKNOWN).count();
        if (down == snapshots.size()) {
            return VectorStoreHealthStatus.DOWN;
        }
        if (up == snapshots.size()) {
            return VectorStoreHealthStatus.UP;
        }
        if (unknown == snapshots.size()) {
            return VectorStoreHealthStatus.UNKNOWN;
        }
        return VectorStoreHealthStatus.DEGRADED;
    }
}
