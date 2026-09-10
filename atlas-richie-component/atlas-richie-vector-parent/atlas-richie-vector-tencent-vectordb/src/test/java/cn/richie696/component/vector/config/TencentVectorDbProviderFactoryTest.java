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

class TencentVectorDbProviderFactoryTest {

    @Test
    void exposesNativeFirstAclSafeHybridOnlyWithAnEncoder() {
        TencentVectorDbProviderFactory factory = new TencentVectorDbProviderFactory(null,
                new SparseVectorizerRegistry(Map.of("bm25", ignored -> new SparseVector(Map.of(1L, 1F)))));

        factory.validateStore(connection(), hybridStore(Map.of("hybrid-enabled", true, "sparse-vectorizer", "bm25")));

        assertThat(factory.capabilities(connection(), hybridStore(Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "bm25"))).supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
        assertThat(factory.capabilities(connection(), plainStore()).supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
    }

    @Test
    void rejectsNonPortableFieldAliasesBeforeACollectionIsCreated() {
        TencentVectorDbProviderFactory factory = new TencentVectorDbProviderFactory(null,
                new SparseVectorizerRegistry(Map.of("bm25", ignored -> new SparseVector(Map.of(1L, 1F)))));

        assertThatThrownBy(() -> factory.validateStore(connection(), hybridStore(Map.of("hybrid-enabled", true,
                "sparse-vectorizer", "bm25", "sparse-field", "lexical"))))
                .hasMessageContaining("SDK default dense/sparse fields");
    }

    private static VectorConnectionDefinition connection() {
        return new VectorConnectionDefinition(VectorConnectionId.of("tencent"), VectorProvider.TENCENT_VECTORDB,
                Map.of("url", "http://example.invalid", "username", "user", "api-key", "redacted"));
    }

    private static VectorStoreDefinition plainStore() {
        return hybridStore(Map.of());
    }

    private static VectorStoreDefinition hybridStore(Map<String, Object> fields) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", "documents", 4, "cosine", "hnsw", 1, 1,
                fields, Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("documents"), VectorConnectionId.of("tencent"), "model",
                "documents", true, Set.of(), Map.of("documents", index));
    }

}
