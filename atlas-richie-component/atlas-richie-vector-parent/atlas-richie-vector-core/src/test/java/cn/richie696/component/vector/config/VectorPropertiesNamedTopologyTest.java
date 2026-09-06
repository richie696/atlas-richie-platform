/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.Modality;
import cn.richie696.component.vector.topology.EmbeddingNormalization;
import cn.richie696.component.vector.topology.VectorTopologyDefinitions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorPropertiesNamedTopologyTest {

    @Test
    void shouldAcceptMilvusAndPostgresqlNamedStores() {
        VectorProperties properties = topology();

        properties.validateNamedTopology();

        assertThat(properties.hasNamedTopology()).isTrue();
        assertThat(properties.getConnections()).containsOnlyKeys("milvus-main", "pg-prompt");
        assertThat(properties.getStores()).containsOnlyKeys("knowledge-rag", "prompt-retrieval");
        assertThat(properties.getStores().get("knowledge-rag").getRequiredCapabilities())
                .containsExactly("ACL_FILTER");
        assertThat(properties.getStores().get("prompt-retrieval").getEmbeddingNormalization())
                .isEqualTo(EmbeddingNormalization.UNSPECIFIED);
        assertThat(properties.getStores().get("prompt-retrieval").getEmbeddingModalities())
                .containsExactly(Modality.TEXT);
        var promptDefinition = VectorTopologyDefinitions.from(properties).stores().stream()
                .filter(store -> store.id().value().equals("prompt-retrieval"))
                .findFirst().orElseThrow();
        assertThat(promptDefinition.queryDefaults().topK()).isEqualTo(20);
        assertThat(promptDefinition.queryDefaults().candidateLimit()).isEqualTo(60);
    }

    @Test
    void shouldKeepLegacyPropertiesValidWithoutNamedTopology() {
        VectorProperties properties = new VectorProperties();
        properties.setDefaultIndex("legacy-documents");

        properties.validateNamedTopology();

        assertThat(properties.hasNamedTopology()).isFalse();
        assertThat(properties.getProvider()).isEqualTo(VectorProvider.MILVUS);
        assertThat(properties.getDefaultIndex()).isEqualTo("legacy-documents");
    }

    @Test
    void shouldRejectDanglingConnectionReference() {
        VectorProperties properties = topology();
        properties.getStores().get("prompt-retrieval").setConnectionRef("missing-pg");

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt-retrieval -> missing-pg");
    }

    @Test
    void shouldRejectConnectionsWithoutStores() {
        VectorProperties properties = topology();
        properties.setStores(Map.of());

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stores must not be empty");
    }

    @Test
    void shouldRejectStoresWithoutConnections() {
        VectorProperties properties = topology();
        properties.setConnections(Map.of());

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connections must not be empty");
    }

    @Test
    void shouldRejectUnknownProviderDuringBinding() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "platform.component.vector.connections.primary.provider", "not-a-provider",
                "platform.component.vector.stores.primary.connection-ref", "primary"));
        Binder binder = new Binder(source);

        assertThatThrownBy(() -> binder.bind(
                "platform.component.vector",
                Bindable.of(VectorProperties.class)))
                .hasMessageContaining("connections.primary.provider")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-provider");
    }

    @Test
    void shouldRejectInvalidStoreIdentifier() {
        VectorProperties properties = topology();
        VectorProperties.StoreConfig store = properties.getStores().remove("prompt-retrieval");
        properties.getStores().put("Prompt Retrieval", store);

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorStoreId must match");
    }

    @Test
    void shouldRejectMissingEmbeddingModelReference() {
        VectorProperties properties = topology();
        properties.getStores().get("knowledge-rag").setEmbeddingModelRef(" ");

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding-model-ref is required: knowledge-rag");
    }

    @Test
    void shouldRejectEmptyEmbeddingModalities() {
        VectorProperties properties = topology();
        properties.getStores().get("knowledge-rag").setEmbeddingModalities(Set.of());

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding-modalities must not be empty: knowledge-rag");
    }

    @Test
    void shouldRejectMalformedCapabilityIdentifier() {
        VectorProperties properties = topology();
        properties.getStores().get("knowledge-rag").setRequiredCapabilities(Set.of("acl-filter"));

        assertThatThrownBy(properties::validateNamedTopology)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid vector capability");
    }

    private static VectorProperties topology() {
        VectorProperties properties = new VectorProperties();
        Map<String, VectorProperties.ConnectionConfig> connections = new LinkedHashMap<>();
        connections.put("milvus-main", new VectorProperties.ConnectionConfig()
                .setProvider(VectorProvider.MILVUS)
                .setSettings(Map.of("host", "milvus.invalid")));
        connections.put("pg-prompt", new VectorProperties.ConnectionConfig()
                .setProvider(VectorProvider.POSTGRESQL)
                .setSettings(Map.of("jdbc-url", "jdbc:postgresql://invalid/prompt")));
        properties.setConnections(connections);

        Map<String, VectorProperties.StoreConfig> stores = new LinkedHashMap<>();
        stores.put("knowledge-rag", new VectorProperties.StoreConfig()
                .setConnectionRef("milvus-main")
                .setEmbeddingModelRef("knowledgeEmbeddingModel")
                .setDefaultIndex("knowledge-documents")
                .setRequiredCapabilities(Set.of("ACL_FILTER")));
        stores.put("prompt-retrieval", new VectorProperties.StoreConfig()
                .setConnectionRef("pg-prompt")
                .setEmbeddingModelRef("promptEmbeddingModel")
                .setQueryDefaults(new VectorProperties.QueryDefaultsConfig()
                        .setTopK(20)
                        .setCandidateLimit(60))
                .setDefaultIndex("prompt-templates"));
        properties.setStores(stores);
        return properties;
    }
}
