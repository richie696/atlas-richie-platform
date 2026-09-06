/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VectorCapabilityDiscoveryTest {

    @Test
    void distinguishesProviderAdapterAndStoreEffectiveCapabilitiesWithoutPhysicalNames() {
        VectorStoreDefinition definition = new VectorStoreDefinition(
                VectorStoreId.of("synthetic-store"),
                VectorConnectionId.of("synthetic-connection"),
                "syntheticEmbeddingModel",
                "logical-index",
                true,
                Set.of());
        VectorStoreCapabilities effective = new VectorStoreCapabilities(List.of(
                new VectorCapabilityDescriptor(
                        VectorCapability.SCORE_STAGES,
                        "1.1",
                        Set.of(VectorCapabilityOperation.SEARCH_TEXT),
                        Map.of("score", "final"))));
        VectorStoreHandle handle = VectorStoreHandle.builder(
                        definition, VectorProvider.REDIS, proxy(VectorService.class))
                .storeCapabilities(effective)
                .build();
        VectorProviderFactory factory = new DiscoveryFactory();
        VectorCapabilityDiscovery discovery = new VectorCapabilityDiscovery(
                new DefaultVectorServiceRegistry(List.of(handle)), List.of(factory));

        VectorStoreCapabilityReport report = discovery.require(VectorStoreId.of("synthetic-store"));

        assertThat(report.providerKnownCapabilities())
                .containsExactlyInAnyOrder("NATIVE_FILTER", "ACL_FILTER", "SCORE_STAGES");
        assertThat(report.adapterExposedCapabilities())
                .containsExactlyInAnyOrder("NATIVE_FILTER", "ACL_FILTER", "SCORE_STAGES");
        assertThat(report.storeEffectiveCapabilities()).containsExactly("SCORE_STAGES");
        assertThat(report.effectiveDescriptors()).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.version()).isEqualTo("1.1");
            assertThat(descriptor.operations()).containsExactly(VectorCapabilityOperation.SEARCH_TEXT);
            assertThat(descriptor.constraints()).containsEntry("score", "final");
        });
        assertThat(report.toString())
                .doesNotContain("logical-index")
                .doesNotContain("synthetic-connection");
    }

    private static final class DiscoveryFactory implements VectorProviderFactory {

        private static final VectorStoreCapabilities ADAPTER = VectorStoreCapabilities.of(
                VectorCapability.NATIVE_FILTER,
                VectorCapability.ACL_FILTER,
                VectorCapability.SCORE_STAGES);

        @Override
        public VectorProvider provider() {
            return VectorProvider.REDIS;
        }

        @Override
        public VectorStoreCapabilities adapterCapabilities() {
            return ADAPTER;
        }

        @Override
        public void validateConnection(VectorConnectionDefinition definition) {
        }

        @Override
        public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        }

        @Override
        public VectorStoreCapabilities capabilities(
                VectorConnectionDefinition connection, VectorStoreDefinition store) {
            return VectorStoreCapabilities.of(VectorCapability.SCORE_STAGES);
        }

        @Override
        public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorStoreHandle createStore(
                VectorConnectionHandle connection,
                VectorStoreDefinition definition,
                VectorEmbeddingModelBinding embeddingModel) {
            throw new UnsupportedOperationException();
        }
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                }));
    }
}
