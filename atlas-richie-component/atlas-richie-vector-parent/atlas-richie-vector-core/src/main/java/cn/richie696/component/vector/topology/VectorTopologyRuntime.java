/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.List;
import java.util.Objects;

/** Owns one immutable store registry and all physical connections behind it. */
public final class VectorTopologyRuntime implements AutoCloseable {

    private final VectorServiceRegistry serviceRegistry;
    private final VectorConnectionRegistry connectionRegistry;
    private final List<VectorStoreStartupFailure> degradedStores;

    VectorTopologyRuntime(
            VectorServiceRegistry serviceRegistry,
            VectorConnectionRegistry connectionRegistry,
            List<VectorStoreStartupFailure> degradedStores) {
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "serviceRegistry must not be null");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry must not be null");
        this.degradedStores = List.copyOf(degradedStores);
    }

    public VectorServiceRegistry serviceRegistry() {
        return serviceRegistry;
    }

    public List<VectorStoreStartupFailure> degradedStores() {
        return degradedStores;
    }

    @Override
    public void close() {
        connectionRegistry.close();
    }
}
