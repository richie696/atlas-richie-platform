/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only capability discovery that never exposes connection settings or physical index names. */
public final class VectorCapabilityDiscovery {

    private final VectorServiceRegistry registry;
    private final Map<VectorProvider, VectorProviderFactory> factories;

    public VectorCapabilityDiscovery(
            VectorServiceRegistry registry, Collection<VectorProviderFactory> providerFactories) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(providerFactories, "providerFactories must not be null");
        EnumMap<VectorProvider, VectorProviderFactory> indexed = new EnumMap<>(VectorProvider.class);
        for (VectorProviderFactory factory : providerFactories) {
            Objects.requireNonNull(factory, "providerFactories must not contain null");
            if (indexed.putIfAbsent(factory.provider(), factory) != null) {
                throw new IllegalArgumentException("duplicate vector provider factory: " + factory.provider());
            }
        }
        this.factories = Map.copyOf(indexed);
    }

    public VectorStoreCapabilityReport require(VectorStoreId storeId) {
        VectorStoreHandle handle = registry.require(storeId);
        VectorProviderFactory factory = factories.get(handle.provider());
        VectorStoreCapabilities providerKnown = factory == null
                ? VectorStoreCapabilities.none() : factory.providerCapabilities();
        VectorStoreCapabilities adapterExposed = factory == null
                ? VectorStoreCapabilities.none() : factory.adapterCapabilities();
        return new VectorStoreCapabilityReport(
                handle.id(),
                handle.provider(),
                providerKnown.ids(),
                adapterExposed.ids(),
                handle.storeCapabilities().ids(),
                handle.storeCapabilities().descriptors());
    }

    public List<VectorStoreCapabilityReport> describeAll() {
        return registry.describeStores().stream().map(descriptor -> require(descriptor.id())).toList();
    }
}
