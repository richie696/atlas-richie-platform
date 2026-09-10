package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.service.SparseVector;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashVectorProviderFactoryTest {

    @Test
    void exposesAclSafeHybridOnlyForAnExplicitSparseReadyStore() {
        DashVectorProviderFactory factory = new DashVectorProviderFactory(null,
                new SparseVectorizerRegistry(Map.of("bm25", ignored -> new SparseVector(Map.of(1L, 1F)))));

        factory.validateStore(connection(), hybridStore(Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "bm25")));

        assertThat(factory.capabilities(connection(), hybridStore(Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "bm25"))).supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
        assertThat(factory.capabilities(connection(), plainStore()).supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
    }

    @Test
    void rejectsHybridDeclarationsThatCannotWriteTheMatchingSparseVector() {
        DashVectorProviderFactory factory = new DashVectorProviderFactory(null,
                new SparseVectorizerRegistry(Map.of()));

        assertThatThrownBy(() -> factory.validateStore(connection(), hybridStore(Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "missing"))))
                .hasMessageContaining("SparseVectorizer");
        assertThatThrownBy(() -> factory.validateStore(connection(), hybridStore(Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "missing", "dense-field", "title_vector"))))
                .hasMessageContaining("SDK default dense/sparse fields");
        assertThatThrownBy(() -> factory.validateStore(connection(), storeWithMetric("cosine", Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "bm25"))))
                .hasMessageContaining("metric ip/dot");
    }

    @Test
    void normalizesTheConfiguredHttpEndpointForTheGrpcSdk() {
        assertThat(DashVectorProviderFactory.sdkEndpoint("https://vrs-cn-test.dashvector.cn-hangzhou.aliyuncs.com/"))
                .isEqualTo("vrs-cn-test.dashvector.cn-hangzhou.aliyuncs.com");
        assertThatThrownBy(() -> DashVectorProviderFactory.sdkEndpoint("https://example.com/api"))
                .hasMessageContaining("without a path");
    }

    @Test
    void rejectsCollectionNamesTheDashVectorServiceWouldRejectAtRuntime() {
        DashVectorProviderFactory factory = new DashVectorProviderFactory(null);
        assertThatThrownBy(() -> factory.validateStore(connection(), storeNamed("a".repeat(33))))
                .hasMessageContaining("3-32 characters");
    }

    private static VectorConnectionDefinition connection() {
        return new VectorConnectionDefinition(VectorConnectionId.of("dash"), VectorProvider.DASHVECTOR,
                Map.of("endpoint", "https://vrs-cn-test.dashvector.cn-hangzhou.aliyuncs.com", "api-key", "redacted"));
    }

    private static VectorStoreDefinition plainStore() {
        return hybridStore(Map.of());
    }

    private static VectorStoreDefinition hybridStore(Map<String, Object> fields) {
        return storeWithMetric("ip", fields);
    }

    private static VectorStoreDefinition storeWithMetric(String metric, Map<String, Object> fields) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", "documents", 4, metric, "hnsw", 1, 1,
                fields, Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("documents"), VectorConnectionId.of("dash"), "model",
                "documents", true, Set.of(), Map.of("documents", index));
    }

    private static VectorStoreDefinition storeNamed(String name) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", name, 4, "cosine", "hnsw", 1, 1,
                Map.of(), Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("documents"), VectorConnectionId.of("dash"), "model",
                "documents", true, Set.of(), Map.of("documents", index));
    }

}
