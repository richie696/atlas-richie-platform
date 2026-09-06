/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

import java.util.List;
import java.util.Set;

/** Redacted three-layer capability report for one concrete Store. */
public record VectorStoreCapabilityReport(
        VectorStoreId storeId,
        VectorProvider provider,
        Set<String> providerKnownCapabilities,
        Set<String> adapterExposedCapabilities,
        Set<String> storeEffectiveCapabilities,
        List<VectorCapabilityDescriptor> effectiveDescriptors) {

    public VectorStoreCapabilityReport {
        providerKnownCapabilities = Set.copyOf(providerKnownCapabilities);
        adapterExposedCapabilities = Set.copyOf(adapterExposedCapabilities);
        storeEffectiveCapabilities = Set.copyOf(storeEffectiveCapabilities);
        effectiveDescriptors = List.copyOf(effectiveDescriptors);
    }
}
