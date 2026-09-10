package cn.richie696.component.vector.config;

import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreId;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantVectorProviderFactoryTest {

    private final QdrantVectorProviderFactory factory = new QdrantVectorProviderFactory(null);

    @Test
    void validatesStrictConnectionAndStoreSettings() {
        factory.validateConnection(connection(Map.of("host", "qdrant.internal", "port", "6334")));
        factory.validateStore(connection(Map.of()), store());

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("poort", 6334))))
                .hasMessage("unknown Qdrant connection settings: [poort]");
        assertThatThrownBy(() -> factory.validateStore(connection(Map.of()), store(Map.of("m", 16))))
                .hasMessageContaining("must be empty");
    }

    @Test
    void advertisesImplementedNamedCapabilitiesWithOperationScope() {
        var capabilities = factory.capabilities(connection(Map.of()), store());
        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "ACL_FILTER", "SCORE_STAGES", "INDEX_LIFECYCLE");
        assertThat(capabilities.descriptor(VectorCapability.ACL_FILTER).orElseThrow().operations())
                .containsExactly(cn.richie696.component.vector.topology.VectorCapabilityOperation.SEARCH_TEXT);
        assertThat(capabilities.descriptor(VectorCapability.ACL_FILTER).orElseThrow().constraints())
                .containsEntry("transport", "qdrant-grpc-filter")
                .containsEntry("nodes", "EQ,IN,CONTAINS_ANY,RANGE,NOT,AND,OR")
                .containsEntry("equality-values", "string,int64");
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
        assertThat(capabilities.supports(VectorCapability.QUERY_TUNING)).isFalse();
    }

    @Test
    void compilesStructuredAclIntoTheNativeQdrantFilterShape() {
        var filter = new cn.richie696.component.vector.filter.QdrantGrpcFilterMapper().map(
                VectorFilter.and(
                        VectorFilter.eq("tenantId", "tenant-a"),
                        VectorFilter.in("principalId", java.util.List.of("user-1", "group-2"))));

        assertThat(filter.getMustCount()).isEqualTo(2);
        assertThat(filter.getMust(0).getField().getKey()).isEqualTo("tenantId");
        assertThat(filter.getMust(0).getField().getMatch().getKeyword()).isEqualTo("tenant-a");
        assertThat(filter.getMust(1).getField().getKey()).isEqualTo("principalId");
        assertThat(filter.getMust(1).getField().getMatch().getKeywords().getStringsList())
                .containsExactly("user-1", "group-2");
    }

    @Test
    void rejectsUnsupportedFilterNodesBeforeAnyProviderRequestCanBeBuilt() {
        assertThatThrownBy(() -> new cn.richie696.component.vector.filter.QdrantGrpcFilterMapper()
                .map(VectorFilter.exists("tenantId")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support exists");
    }

    @Test
    void createsBoundHandleAndClosesClient() {
        VectorConnectionHandle connection = factory.openConnection(connection(Map.of()));
        try {
            var handle = factory.createStore(
                    connection, store(), VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            assertThat(handle.requireCapability(VectorScoreSemantics.class).descriptors()).hasSize(1);
            assertThat(handle.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThat(handle.requireCapability(VectorFilterCompiler.class)).isNotNull();
            assertThatThrownBy(() -> handle.service().searchByText(
                    "other", "query", 3, SearchOptions.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this Qdrant Store");
        } finally {
            connection.close();
        }
    }

    @Test
    void registersFactoryWithoutCreatingLegacyClientOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(QdrantVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(QdrantVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(QdrantClient.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("qdrant"), VectorProvider.QDRANT, settings);
    }

    private static VectorStoreDefinition store() {
        return store(Map.of());
    }

    private static VectorStoreDefinition store(Map<String, Object> indexParams) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                "documents", "documents_qdrant", 4, "cosine", "hnsw", 1, 1, Map.of(), indexParams);
        return new VectorStoreDefinition(
                VectorStoreId.of("documents"), VectorConnectionId.of("qdrant"), "model",
                "documents", true, Set.of(), Map.of("documents", index));
    }

    private static EmbeddingModel embeddingModel(int dimensions) {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(), new Class<?>[]{EmbeddingModel.class},
                (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return dimensions;
                    if ("embed".equals(method.getName())) return new float[dimensions];
                    if ("toString".equals(method.getName())) return "FakeEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
