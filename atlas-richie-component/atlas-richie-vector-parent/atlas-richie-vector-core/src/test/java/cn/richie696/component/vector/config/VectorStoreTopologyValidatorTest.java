/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorCapabilityDescriptor;
import cn.richie696.component.vector.topology.VectorTopologyDefinitions;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreTopologyValidatorTest {

    @Test
    void shouldLeaveLegacyModeUnchangedWithoutFactories() {
        VectorProperties properties = new VectorProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.vector.provider", "milvus");

        VectorTopologyDefinitions definitions = new VectorStoreTopologyValidator(
                properties, environment, Set.of()).validate();

        assertThat(definitions.connections()).isEmpty();
        assertThat(definitions.stores()).isEmpty();
    }

    @Test
    void shouldValidateAndConvertNamedTopology() {
        VectorProperties properties = namedProperties();
        MockEnvironment environment = namedEnvironment();
        StrictFakeFactory factory = new StrictFakeFactory();

        VectorTopologyDefinitions definitions = new VectorStoreTopologyValidator(
                properties, environment, Set.of(factory)).validate();

        assertThat(definitions.connections()).hasSize(1);
        assertThat(definitions.stores()).hasSize(1);
        assertThat(definitions.stores().getFirst().indexes()).containsOnlyKeys("documents");
        assertThat(factory.connectionValidations).hasValue(1);
        assertThat(factory.storeValidations).hasValue(1);
    }

    @Test
    void shouldRejectUnknownStoreAndConnectionFields() {
        VectorProperties properties = namedProperties();
        MockEnvironment storeEnvironment = namedEnvironment()
                .withProperty("platform.component.vector.stores.primary.connection-reff", "primary");
        MockEnvironment connectionEnvironment = namedEnvironment()
                .withProperty("platform.component.vector.connections.primary.providerr", "milvus");

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, storeEnvironment, Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stores.primary.connection-reff");
        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, connectionEnvironment, Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connections.primary.providerr");
    }

    @Test
    void shouldRejectLegacyAndNamedConfigurationConflict() {
        VectorProperties properties = namedProperties();
        MockEnvironment environment = namedEnvironment()
                .withProperty("platform.component.vector.provider", "milvus")
                .withProperty("platform.component.vector.milvus.host", "legacy.invalid");

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, environment, Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be combined with legacy")
                .hasMessageNotContaining("legacy.invalid");
    }

    @Test
    void shouldRejectMissingProviderFactory() {
        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                namedProperties(), namedEnvironment(), Set.of()).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vector provider factory is not available: MILVUS");
    }

    @Test
    void shouldSanitizeProviderValidationFailure() {
        VectorProperties properties = namedProperties();
        properties.getConnections().get("primary").setSettings(Map.of("password", "secret-value"));
        MockEnvironment environment = namedEnvironment()
                .withProperty("platform.component.vector.connections.primary.settings.password", "secret-value");

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, environment, Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vector connection validation failed: primary")
                .hasMessageContaining(IllegalArgumentException.class.getName())
                .hasMessageNotContaining("secret-value")
                .hasNoCause();
    }

    @Test
    void shouldRejectUndeclaredDefaultIndexWhenIndexesAreDeclared() {
        VectorProperties properties = namedProperties();
        properties.getStores().get("primary").setDefaultIndex("missing-index");

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, namedEnvironment(), Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vector store default index is not declared: primary");
    }

    @Test
    void shouldRequireOnlyExplicitlyConfiguredCapabilities() {
        VectorProperties properties = namedProperties();
        properties.getStores().get("primary").setRequiredCapabilities(Set.of("ACL_FILTER"));

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, namedEnvironment(), Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primary -> [ACL_FILTER]");

        VectorTopologyDefinitions definitions = new VectorStoreTopologyValidator(
                properties,
                namedEnvironment(),
                Set.of(new StrictFakeFactory(VectorStoreCapabilities.of(VectorCapability.ACL_FILTER))))
                .validate();
        assertThat(definitions.stores()).hasSize(1);
    }

    @Test
    void shouldRejectTwoStoresThatResolveToTheSamePhysicalResourceOnOneConnection() {
        VectorProperties properties = namedProperties();
        VectorProperties.StoreConfig second = new VectorProperties.StoreConfig()
                .setConnectionRef("primary")
                .setIndexes(new LinkedHashMap<>(properties.getStores().get("primary").getIndexes()));
        Map<String, VectorProperties.StoreConfig> stores = new LinkedHashMap<>(properties.getStores());
        stores.put("secondary", second);
        properties.setStores(stores);
        MockEnvironment environment = namedEnvironment()
                .withProperty("platform.component.vector.stores.secondary.connection-ref", "primary")
                .withProperty("platform.component.vector.stores.secondary.indexes.documents.name", "documents")
                .withProperty("platform.component.vector.stores.secondary.indexes.documents.dimension", "1536")
                .withProperty("platform.component.vector.stores.secondary.indexes.documents.metric", "cosine")
                .withProperty("platform.component.vector.stores.secondary.indexes.documents.index-type", "hnsw")
                .withProperty("platform.component.vector.stores.secondary.indexes.documents.replicas", "1")
                .withProperty("platform.component.vector.stores.secondary.indexes.documents.shards", "1");

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, environment, Set.of(new StrictFakeFactory(
                        VectorStoreCapabilities.none(), Set.of("collection:documents")))).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vector stores resolve to the same physical resource on connection primary: primary, secondary")
                .hasMessageNotContaining("collection:documents");
    }

    @Test
    void shouldRejectStoreQueryDefaultsWithoutTypedAdvancedEntryPoint() {
        VectorProperties properties = namedProperties();
        properties.getStores().get("primary").setQueryDefaults(
                new VectorProperties.QueryDefaultsConfig().setTopK(25));
        MockEnvironment environment = namedEnvironment().withProperty(
                "platform.component.vector.stores.primary.query-defaults.top-k", "25");

        assertThatThrownBy(() -> new VectorStoreTopologyValidator(
                properties, environment, Set.of(new StrictFakeFactory())).validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query-defaults require the typed advanced query capability: primary");

        VectorStoreCapabilities typed = new VectorStoreCapabilities(List.of(
                new VectorCapabilityDescriptor(
                        VectorCapability.QUERY_TUNING,
                        "1.0",
                        Map.of("api", VectorAdvancedSearchOperations.class.getName()))));
        assertThat(new VectorStoreTopologyValidator(
                properties, environment, Set.of(new StrictFakeFactory(typed))).validate().stores())
                .hasSize(1);
    }

    private static VectorProperties namedProperties() {
        VectorProperties properties = new VectorProperties();
        properties.setConnections(Map.of(
                "primary", new VectorProperties.ConnectionConfig()
                        .setProvider(VectorProvider.MILVUS)
                        .setSettings(Map.of("host", "primary.invalid"))));

        VectorProperties.IndexConfig index = new VectorProperties.IndexConfig()
                .setName("documents")
                .setDimension(1536)
                .setMetric("cosine")
                .setIndexType("hnsw")
                .setReplicas(1)
                .setShards(1);
        Map<String, VectorProperties.IndexConfig> indexes = new LinkedHashMap<>();
        indexes.put("documents", index);
        properties.setStores(Map.of(
                "primary", new VectorProperties.StoreConfig()
                        .setConnectionRef("primary")
                        .setIndexes(indexes)));
        return properties;
    }

    private static MockEnvironment namedEnvironment() {
        return new MockEnvironment()
                .withProperty("platform.component.vector.connections.primary.provider", "milvus")
                .withProperty("platform.component.vector.connections.primary.settings.host", "primary.invalid")
                .withProperty("platform.component.vector.stores.primary.connection-ref", "primary")
                .withProperty("platform.component.vector.stores.primary.indexes.documents.name", "documents")
                .withProperty("platform.component.vector.stores.primary.indexes.documents.dimension", "1536")
                .withProperty("platform.component.vector.stores.primary.indexes.documents.metric", "cosine")
                .withProperty("platform.component.vector.stores.primary.indexes.documents.index-type", "hnsw")
                .withProperty("platform.component.vector.stores.primary.indexes.documents.replicas", "1")
                .withProperty("platform.component.vector.stores.primary.indexes.documents.shards", "1");
    }

    private static final class StrictFakeFactory implements VectorProviderFactory {

        private final AtomicInteger connectionValidations = new AtomicInteger();
        private final AtomicInteger storeValidations = new AtomicInteger();
        private final VectorStoreCapabilities effectiveCapabilities;
        private final Set<String> physicalResourceIdentities;

        private StrictFakeFactory() {
            this(VectorStoreCapabilities.none(), Set.of());
        }

        private StrictFakeFactory(VectorStoreCapabilities effectiveCapabilities) {
            this(effectiveCapabilities, Set.of());
        }

        private StrictFakeFactory(
                VectorStoreCapabilities effectiveCapabilities,
                Set<String> physicalResourceIdentities) {
            this.effectiveCapabilities = effectiveCapabilities;
            this.physicalResourceIdentities = physicalResourceIdentities;
        }

        @Override
        public VectorProvider provider() {
            return VectorProvider.MILVUS;
        }

        @Override
        public void validateConnection(VectorConnectionDefinition definition) {
            connectionValidations.incrementAndGet();
            if (!definition.settings().keySet().equals(Set.of("host"))) {
                throw new IllegalArgumentException("invalid settings with secret-value");
            }
        }

        @Override
        public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
            storeValidations.incrementAndGet();
        }

        @Override
        public VectorStoreCapabilities capabilities(
                VectorConnectionDefinition connection,
                VectorStoreDefinition store) {
            return effectiveCapabilities;
        }

        @Override
        public Set<String> physicalResourceIdentities(
                VectorConnectionDefinition connection, VectorStoreDefinition store) {
            return physicalResourceIdentities;
        }

        @Override
        public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
            throw new UnsupportedOperationException("not used by validation tests");
        }

        @Override
        public VectorStoreHandle createStore(
                VectorConnectionHandle connection,
                VectorStoreDefinition definition,
                VectorEmbeddingModelBinding embeddingModel) {
            throw new UnsupportedOperationException("not used by validation tests");
        }
    }
}
