package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorContent;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.service.SparseVector;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreId;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Tencent VectorDB E2E; the native path may safely use classified Core RRF fallback. */
@EnabledIfEnvironmentVariable(named = "VECTOR_TENCENT_VECTORDB_IT_RUN", matches = "true")
class TencentVectorDbAclSafeHybridLiveIT {

    @Test
    void writesBothVectorsAndNeverReturnsTheDeniedSparseWinner() {
        String collection = "atlas_acl_" + UUID.randomUUID().toString().replace("-", "");
        TencentVectorDbProviderFactory factory = new TencentVectorDbProviderFactory(null,
                new SparseVectorizerRegistry(Map.of("testSparse", text -> new SparseVector(
                        Map.of(text.contains("rare") ? 2L : 1L, 1F)))));
        VectorConnectionDefinition definition = new VectorConnectionDefinition(VectorConnectionId.of("tencent"),
                VectorProvider.TENCENT_VECTORDB, Map.of("url", required("TENCENT_VECTORDB_URL"),
                "username", required("TENCENT_VECTORDB_USERNAME"), "api-key", required("TENCENT_VECTORDB_API_KEY")));
        var connection = factory.openConnection(definition);
        VectorStoreHandle handle = null;
        try {
            handle = factory.createStore(connection, store(collection), VectorEmbeddingModelBinding.of("test", embedding()));
            handle.service().upsert(record("allowed", "ordinary allowed", "tenant-a"));
            handle.service().upsert(record("denied", "rare restricted", "tenant-b"));
            VectorAclAwareHybridSearchOperations hybrid = handle.requireCapability(VectorAclAwareHybridSearchOperations.class);
            assertThat(((HybridSearchExecutionMode) hybrid).hybridExecutionMode()).isEqualTo("native");
            var results = hybrid.hybridSearch("documents", "ordinary", "rare", 5,
                    HybridSearchOptions.builder().vectorWeight(.3D).keywordWeight(.7D).build(),
                    VectorFilter.eq("tenantId", "tenant-a"));
            assertThat(results).extracting(result -> result.getId()).contains("allowed").doesNotContain("denied");
            handle.service().deleteById("documents", "allowed");
            assertThat(hybrid.hybridSearch("documents", "ordinary", "ordinary", 5, null,
                    VectorFilter.eq("tenantId", "tenant-a"))).extracting(result -> result.getId()).doesNotContain("allowed");
            connection.close();
            connection = factory.openConnection(definition);
            handle = factory.createStore(connection, store(collection), VectorEmbeddingModelBinding.of("test", embedding()));
            VectorAclAwareHybridSearchOperations reopened = handle.requireCapability(VectorAclAwareHybridSearchOperations.class);
            assertThat(reopened.hybridSearch("documents", "ordinary", "ordinary", 5, null,
                    VectorFilter.eq("tenantId", "tenant-a"))).extracting(result -> result.getId()).doesNotContain("allowed");
        } finally {
            if (handle != null) {
                handle.requireCapability(cn.richie696.ai.vectorstore.tencentvectordb.api.TencentVectorDbCollectionOperations.class)
                        .dropCollection("spring_ai", collection);
            }
            connection.close();
        }
    }

    private static VectorStoreDefinition store(String collection) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", collection, 4, "cosine", "hnsw", 1, 1,
                Map.of("database-name", "spring_ai", "initialize-schema", true, "hybrid-enabled", true,
                        "sparse-vectorizer", "testSparse", "hybrid-candidate-limit", 10,
                        "hybrid-fallback-mode", "core-rrf"), Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("tencent-docs"), VectorConnectionId.of("tencent"), "test",
                "documents", true, Set.of(), Map.of("documents", index));
    }

    private static VectorRecord record(String id, String text, String tenant) {
        VectorRecord record = new VectorRecord();
        record.setId(id); record.setIndexName("documents"); record.setContent(new VectorContent.TextContent(text, "text/plain"));
        record.setMetadata(Map.of("tenantId", tenant));
        return record;
    }

    private static EmbeddingModel embedding() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class}, (proxy, method, args) -> {
                    if ("dimensions".equals(method.getName())) return 4;
                    if ("embed".equals(method.getName())) return new float[]{1F, 0F, 0F, 0F};
                    return null;
                }));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing integration-test environment variable: " + name);
        return value;
    }
}
