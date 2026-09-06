/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Collection;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable capabilities proven for one Store/Provider/index binding. */
public final class VectorStoreCapabilities {

    private static final VectorStoreCapabilities NONE = new VectorStoreCapabilities(Set.of());

    private final Map<VectorCapability, VectorCapabilityDescriptor> descriptors;

    public VectorStoreCapabilities(Collection<VectorCapabilityDescriptor> descriptors) {
        EnumMap<VectorCapability, VectorCapabilityDescriptor> indexed = new EnumMap<>(VectorCapability.class);
        for (VectorCapabilityDescriptor descriptor : descriptors) {
            VectorCapabilityDescriptor previous = indexed.putIfAbsent(descriptor.capability(), descriptor);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate vector capability: " + descriptor.capability().id());
            }
        }
        this.descriptors = Map.copyOf(indexed);
    }

    public static VectorStoreCapabilities none() {
        return NONE;
    }

    public static VectorStoreCapabilities of(VectorCapability... capabilities) {
        return new VectorStoreCapabilities(java.util.Arrays.stream(capabilities)
                .map(VectorCapabilityDescriptor::supported)
                .toList());
    }

    public boolean supports(VectorCapability capability) {
        return descriptors.containsKey(capability);
    }

    public Optional<VectorCapabilityDescriptor> descriptor(VectorCapability capability) {
        return Optional.ofNullable(descriptors.get(capability));
    }

    public Set<String> ids() {
        return descriptors.keySet().stream().map(VectorCapability::id).collect(Collectors.toUnmodifiableSet());
    }

    public List<VectorCapabilityDescriptor> descriptors() {
        return descriptors.values().stream().toList();
    }

    public void requireAll(VectorStoreId storeId, Set<String> requiredCapabilities) {
        Set<String> missing = requiredCapabilities.stream()
                .map(VectorCapability::fromId)
                .filter(capability -> !supports(capability))
                .map(VectorCapability::id)
                .collect(Collectors.toUnmodifiableSet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("vector store required capabilities are not available: "
                    + storeId + " -> " + missing);
        }
    }
}
