/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreCapabilitiesTest {

    @Test
    void shouldKeepBasicStoreCapabilityFree() {
        VectorStoreCapabilities capabilities = VectorStoreCapabilities.none();

        assertThat(capabilities.ids()).isEmpty();
        assertThat(capabilities.supports(VectorCapability.NATIVE_FILTER)).isFalse();
        capabilities.requireAll(VectorStoreId.of("basic-store"), Set.of());
    }

    @Test
    void shouldExposeVersionedCapabilityAndConstraints() {
        VectorCapabilityDescriptor descriptor = new VectorCapabilityDescriptor(
                VectorCapability.ACL_SAFE_HYBRID,
                "1.0",
                Map.of("filter-stage", "provider-recall"));
        VectorStoreCapabilities capabilities = new VectorStoreCapabilities(List.of(descriptor));

        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
        assertThat(capabilities.descriptor(VectorCapability.ACL_SAFE_HYBRID)).contains(descriptor);
        assertThat(capabilities.ids()).containsExactly("ACL_SAFE_HYBRID");
    }

    @Test
    void shouldRejectUnknownOrMissingRequiredCapabilities() {
        VectorStoreCapabilities capabilities = VectorStoreCapabilities.of(VectorCapability.NATIVE_FILTER);

        assertThatThrownBy(() -> capabilities.requireAll(
                VectorStoreId.of("secure-store"), Set.of("ACL_SAFE_HYBRID")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vector store required capabilities are not available: secure-store -> [ACL_SAFE_HYBRID]");
        assertThatThrownBy(() -> capabilities.requireAll(
                VectorStoreId.of("secure-store"), Set.of("NOT_REAL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown vector capability: NOT_REAL");
    }

    @Test
    void shouldRejectDuplicateCapabilityDescriptors() {
        VectorCapabilityDescriptor descriptor = VectorCapabilityDescriptor.supported(VectorCapability.QUERY_TUNING);

        assertThatThrownBy(() -> new VectorStoreCapabilities(List.of(descriptor, descriptor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate vector capability: QUERY_TUNING");
    }
}
