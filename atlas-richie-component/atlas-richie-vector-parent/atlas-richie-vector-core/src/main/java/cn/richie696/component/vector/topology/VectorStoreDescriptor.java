/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.Modality;

import java.util.Set;

/** Redacted, immutable store description safe for diagnostics and capability discovery. */
public record VectorStoreDescriptor(
        VectorStoreId id,
        VectorConnectionId connectionId,
        VectorProvider provider,
        boolean required,
        String embeddingModelFingerprint,
        EmbeddingNormalization embeddingNormalization,
        Set<Modality> embeddingModalities,
        Set<String> requiredCapabilities,
        Set<String> exposedCapabilityTypes,
        Set<String> effectiveCapabilities) {

    public VectorStoreDescriptor {
        embeddingModalities = Set.copyOf(embeddingModalities);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        exposedCapabilityTypes = Set.copyOf(exposedCapabilityTypes);
        effectiveCapabilities = Set.copyOf(effectiveCapabilities);
    }
}
