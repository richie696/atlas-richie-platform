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

/** Real DashVector E2E for the platform writer and native ACL-filtered hybrid request. */
@EnabledIfEnvironmentVariable(named = "VECTOR_DASHVECTOR_IT_RUN", matches = "true")
class DashVectorAclSafeHybridLiveIT {

    @Test
    void writesDenseAndSparseTogetherAndExcludesDeniedCandidate() {
        String collection = "acl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        String denseCollection = "acl2_" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
        DashVectorProviderFactory factory = new DashVectorProviderFactory(null,
                new SparseVectorizerRegistry(Map.of("testSparse", text -> new SparseVector(
                        Map.of(text.contains("rare") ? 2L : 1L, 1F)))));
        var connection = factory.openConnection(new VectorConnectionDefinition(VectorConnectionId.of("dash"),
                VectorProvider.DASHVECTOR, Map.of("endpoint", required("DASHVECTOR_ENDPOINT"),
                "api-key", required("DASHVECTOR_API_KEY"))));
        VectorStoreHandle hybridHandle = null;
        VectorStoreHandle denseHandle = null;
        try {
            hybridHandle = factory.createStore(connection, hybridStore(collection), VectorEmbeddingModelBinding.of("test", embedding()));
            denseHandle = factory.createStore(connection, denseStore(denseCollection), VectorEmbeddingModelBinding.of("test", embedding()));
            hybridHandle.service().upsert(record("allowed", "ordinary allowed", "tenant-a"));
            hybridHandle.service().upsert(record("denied", "rare restricted", "tenant-b"));
            denseHandle.service().upsert(record("isolated", "ordinary separate store", "tenant-a"));
            VectorAclAwareHybridSearchOperations hybrid = hybridHandle.requireCapability(VectorAclAwareHybridSearchOperations.class);
            assertThat(((HybridSearchExecutionMode) hybrid).hybridExecutionMode()).isEqualTo("native");
            var results = hybrid.hybridSearch("documents", "ordinary", "rare", 5,
                    HybridSearchOptions.builder().vectorWeight(.3D).keywordWeight(.7D).build(),
                    VectorFilter.eq("tenantId", "tenant-a"));
            assertThat(results).extracting(result -> result.getId()).contains("allowed").doesNotContain("denied", "isolated");
            hybridHandle.service().deleteById("documents", "allowed");
            assertThat(hybrid.hybridSearch("documents", "ordinary", "ordinary", 5, null,
                    VectorFilter.eq("tenantId", "tenant-a"))).extracting(result -> result.getId()).doesNotContain("allowed");
        } finally {
            if (denseHandle != null) {
                denseHandle.requireCapability(cn.richie696.ai.vectorstore.dashvector.api.DashVectorCollectionOperations.class)
                        .deleteCollection(denseCollection);
            }
            if (hybridHandle != null) {
                hybridHandle.requireCapability(cn.richie696.ai.vectorstore.dashvector.api.DashVectorCollectionOperations.class)
                        .deleteCollection(collection);
            }
            connection.close();
        }
    }

    private static VectorStoreDefinition hybridStore(String collection) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", collection, 4, "ip", "hnsw", 1, 1,
                Map.of("initialize-schema", true, "metadata-fields", "tenantId:STRING", "hybrid-enabled", true,
                        "sparse-vectorizer", "testSparse", "hybrid-candidate-limit", 10), Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("dash-docs"), VectorConnectionId.of("dash"), "test",
                "documents", true, Set.of(), Map.of("documents", index));
    }

    private static VectorStoreDefinition denseStore(String collection) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", collection, 4, "cosine", "hnsw", 1, 1,
                Map.of("initialize-schema", true, "metadata-fields", "tenantId:STRING"), Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("dash-dense-docs"), VectorConnectionId.of("dash"), "test",
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
                    if ("embed".equals(method.getName())) {
                        if (args != null && args.length > 0 && args[0] instanceof java.util.List<?> texts) {
                            return texts.stream().map(ignored -> new float[]{1F, 0F, 0F, 0F}).toList();
                        }
                        return new float[]{1F, 0F, 0F, 0F};
                    }
                    return null;
                }));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing integration-test environment variable: " + name);
        return value;
    }
}
