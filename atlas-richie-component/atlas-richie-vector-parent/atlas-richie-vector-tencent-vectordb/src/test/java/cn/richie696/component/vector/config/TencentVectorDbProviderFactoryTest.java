package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.service.SparseVector;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import com.tencent.tcvectordb.client.VectorDBClient;
import com.tencent.tcvectordb.model.Collection;
import com.tencent.tcvectordb.model.param.collection.FieldType;
import com.tencent.tcvectordb.model.param.collection.IndexField;
import com.tencent.tcvectordb.model.param.collection.IndexType;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class TencentVectorDbProviderFactoryTest {

    @Test
    void opensSdkConnectionAndBuildsPlainStoreHandle() {
        TencentVectorDbProviderFactory factory = new TencentVectorDbProviderFactory(null);
        try (var ignored = org.mockito.Mockito.mockConstruction(VectorDBClient.class)) {
            VectorConnectionHandle connection = factory.openConnection(connection());
            try {
                assertThat(factory.provider()).isEqualTo(VectorProvider.TENCENT_VECTORDB);
                assertThat(factory.adapterCapabilities().ids()).contains("NATIVE_FILTER", "ACL_FILTER");
                assertThat(factory.physicalResourceIdentities(connection(), plainStore()))
                        .containsExactly("collection:spring_ai.documents");
                var handle = factory.createStore(connection, plainStore(),
                        VectorEmbeddingModelBinding.of("model", embeddingModel()));
                assertThat(handle).isNotNull();
                assertThat(connection.toString()).contains("config=<redacted>");
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void validatesSdkSpecificConnectionAndIndexVariants() {
        TencentVectorDbProviderFactory factory = new TencentVectorDbProviderFactory(null);
        factory.validateConnection(new VectorConnectionDefinition(VectorConnectionId.of("tencent"),
                VectorProvider.TENCENT_VECTORDB, Map.of(
                "url", "http://example.invalid", "username", "user", "api-key", "redacted",
                "timeout-seconds", "20", "connect-timeout-seconds", 30,
                "read-consistency", "eventual_consistency")));
        factory.validateStore(connection(), storeWithMetric("l2", Map.of()));
        factory.validateStore(connection(), storeWithMetric("ip", Map.of()));
        assertThatThrownBy(() -> factory.validateStore(connection(), storeWithMetric("manhattan", Map.of())))
                .hasMessageContaining("unsupported Tencent VectorDB metric");
        assertThatThrownBy(() -> factory.validateStore(connection(),
                new VectorStoreDefinition(VectorStoreId.of("documents"), VectorConnectionId.of("tencent"),
                        "model", "documents", true, Set.of(), Map.of())))
                .hasMessageContaining("exactly one bound index");
    }

    @Test
    void buildsExplicitHybridStoreAfterValidatingNativeIndexes() {
        TencentVectorDbProviderFactory factory = new TencentVectorDbProviderFactory(null,
                new SparseVectorizerRegistry(Map.of("bm25", ignored -> new SparseVector(Map.of(1L, 1F)))));
        Collection collection = new Collection();
        IndexField dense = new IndexField();
        dense.setFieldName("vector");
        dense.setFieldType(FieldType.Vector);
        dense.setIndexType(IndexType.HNSW);
        IndexField sparse = new IndexField();
        sparse.setFieldName("sparse_vector");
        sparse.setFieldType(FieldType.SparseVector);
        sparse.setIndexType(IndexType.INVERTED);
        collection.setIndexes(List.of(dense, sparse));

        try (var ignored = org.mockito.Mockito.mockConstruction(VectorDBClient.class,
                (client, context) -> when(client.describeCollection(anyString(), anyString())).thenReturn(collection))) {
            VectorConnectionHandle connection = factory.openConnection(connection());
            try {
                var handle = factory.createStore(connection,
                        hybridStore(Map.of("hybrid-enabled", true, "sparse-vectorizer", "bm25")),
                        VectorEmbeddingModelBinding.of("model", embeddingModel()));
                assertThat(handle.storeCapabilities().supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
            } finally {
                connection.close();
            }
        }
    }

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
        return storeWithMetric("cosine", fields);
    }

    private static VectorStoreDefinition storeWithMetric(String metric, Map<String, Object> fields) {
        VectorIndexDefinition index = new VectorIndexDefinition("documents", "documents", 4, metric, "hnsw", 1, 1,
                fields, Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("documents"), VectorConnectionId.of("tencent"), "model",
                "documents", true, Set.of(), Map.of("documents", index));
    }

    private static EmbeddingModel embeddingModel() {
        return (EmbeddingModel) Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(), new Class<?>[]{EmbeddingModel.class},
                (proxy, method, args) -> {
                    if ("dimensions".equals(method.getName())) return 4;
                    if ("toString".equals(method.getName())) return "TestEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }

}
