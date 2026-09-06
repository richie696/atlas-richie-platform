/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.exceptions.VectorStoreNotExistException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable and thread-safe registry implementation. */
public final class DefaultVectorServiceRegistry implements VectorServiceRegistry {

    private final Map<VectorStoreId, VectorStoreHandle> stores;
    private final List<VectorStoreDescriptor> descriptors;

    public DefaultVectorServiceRegistry(Collection<VectorStoreHandle> handles) {
        Objects.requireNonNull(handles, "handles must not be null");
        Map<VectorStoreId, VectorStoreHandle> registered = new LinkedHashMap<>();
        for (VectorStoreHandle handle : handles) {
            Objects.requireNonNull(handle, "handles must not contain null");
            if (registered.putIfAbsent(handle.id(), handle) != null) {
                throw new IllegalArgumentException("duplicate vector store id: " + handle.id());
            }
        }
        this.stores = Map.copyOf(registered);
        this.descriptors = registered.values().stream()
                .map(VectorStoreHandle::descriptor)
                .toList();
    }

    @Override
    public Optional<VectorStoreHandle> find(VectorStoreId storeId) {
        return Optional.ofNullable(stores.get(Objects.requireNonNull(storeId, "storeId must not be null")));
    }

    @Override
    public VectorStoreHandle require(VectorStoreId storeId) {
        return find(storeId).orElseThrow(() -> new VectorStoreNotExistException(storeId.value()));
    }

    @Override
    public List<VectorStoreDescriptor> describeStores() {
        return descriptors;
    }
}
