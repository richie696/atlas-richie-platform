/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreId;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusVectorProviderFactoryTest {

    private final MilvusVectorProviderFactory factory = new MilvusVectorProviderFactory(null);

    @Test
    void shouldAcceptSafeDefaultsAndStrictKnownConnectionSettings() {
        factory.validateConnection(connection(Map.of()));
        factory.validateConnection(connection(Map.of(
                "host", "milvus.internal",
                "port", "19530",
                "secure", "false",
                "connect-timeout-ms", 5_000)));

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("hosst", "typo.invalid"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown Milvus connection settings: [hosst]");
        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("port", 70_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port must be between");
        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("secure", "not-boolean"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Milvus setting must be a boolean: secure");
    }

    @Test
    void shouldValidatePhysicalIndexNamesTypesAndMetrics() {
        factory.validateStore(connection(Map.of()), store("documents", "hnsw", "cosine"));
        factory.validateStore(connection(Map.of()), store("documents_2", "ivf_flat", "l2"));

        assertThatThrownBy(() -> factory.validateStore(
                connection(Map.of()), store("invalid-name", "hnsw", "cosine")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Milvus default index");
        assertThatThrownBy(() -> factory.validateStore(
                connection(Map.of()), store("documents", "not-real", "cosine")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported Milvus index type: not-real");
        assertThatThrownBy(() -> factory.validateStore(
                connection(Map.of()), store("documents", "hnsw", "not-real")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported Milvus metric: not-real");
    }

    @Test
    void shouldAdvertiseOnlyImplementedStoreCapabilities() {
        var capabilities = factory.capabilities(connection(Map.of()), store("documents", "hnsw", "cosine"));

        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "ACL_FILTER", "QUERY_TUNING", "SCORE_STAGES", "INDEX_LIFECYCLE");
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
        assertThat(capabilities.supports(VectorCapability.CANDIDATE_VECTOR)).isFalse();
    }

    @Test
    void shouldExposeAclSafeHybridOnlyForExplicitlyHybridEnabledStore() {
        var capabilities = factory.capabilities(connection(Map.of()),
                store("documents", "hnsw", "cosine", Map.of("hybrid-enabled", true)));

        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
        assertThat(capabilities.descriptor(VectorCapability.ACL_SAFE_HYBRID).orElseThrow().constraints())
                .containsEntry("filter-stage", "provider-recall")
                .containsEntry("schema", "hybrid-enabled");
        assertThat(capabilities.supports(VectorCapability.CANDIDATE_VECTOR)).isFalse();
    }

    @Test
    void shouldRegisterFactoryWithoutCreatingLegacyClientOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MilvusVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(MilvusVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(MilvusServiceClient.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("primary"), VectorProvider.MILVUS, settings);
    }

    private static VectorStoreDefinition store(String indexName, String indexType, String metric) {
        return store(indexName, indexType, metric, Map.of());
    }

    private static VectorStoreDefinition store(
            String indexName, String indexType, String metric, Map<String, Object> additionalFields) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                indexName, indexName, 1536, metric, indexType, 1, 1, additionalFields, Map.of());
        return new VectorStoreDefinition(
                VectorStoreId.of("primary"),
                VectorConnectionId.of("primary"),
                "aiEmbeddingModel",
                indexName,
                true,
                Set.of(),
                Map.of(indexName, index));
    }
}
