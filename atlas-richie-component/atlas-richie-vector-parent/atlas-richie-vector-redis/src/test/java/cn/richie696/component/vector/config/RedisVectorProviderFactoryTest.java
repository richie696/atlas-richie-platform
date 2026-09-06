package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
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
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import redis.clients.jedis.RedisClient;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisVectorProviderFactoryTest {

    private final RedisVectorProviderFactory factory = new RedisVectorProviderFactory(null);

    @Test
    void validatesStrictConnectionStoreAndAppliedHnswSettings() {
        factory.validateConnection(connection(Map.of("host", "redis.internal", "ssl", true)));
        factory.validateStore(connection(Map.of()), store("documents", "documents_idx",
                Map.of("metadata-fields", "tenantId:tag,groupId:tag,year:numeric"),
                Map.of("M", 24, "efConstruction", 300, "efRuntime", 80)));

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("poort", 6379))))
                .hasMessage("unknown Redis connection settings: [poort]");
        assertThatThrownBy(() -> factory.validateStore(connection(Map.of()), store(
                "documents", "documents_idx", Map.of(), Map.of("efSearch", 50))))
                .hasMessage("unknown Redis index-params: [efSearch]");
        assertThatThrownBy(() -> factory.validateStore(connection(Map.of()), store(
                "documents", "documents_idx", Map.of("metadata-fields", "tenantId:keyword"), Map.of())))
                .hasMessage("unsupported Redis metadata field type: keyword");
    }

    @Test
    void advertisesOnlyImplementedNamedCapabilities() {
        var capabilities = factory.capabilities(connection(Map.of()), store());
        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "ACL_FILTER", "SCORE_STAGES", "INDEX_LIFECYCLE");
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
        assertThat(capabilities.supports(VectorCapability.QUERY_TUNING)).isFalse();
        assertThat(factory.capabilities(connection(Map.of()),
                store("plain", "plain_idx", Map.of(), Map.of())).ids())
                .containsExactlyInAnyOrder("SCORE_STAGES", "INDEX_LIFECYCLE");
    }

    @Test
    void createsTwoStoreBoundHandlesOnOneConnectionWithoutNetworkAccess() {
        VectorConnectionHandle connection = factory.openConnection(connection(Map.of()));
        try {
            var documents = factory.createStore(connection, store(),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            var prompts = factory.createStore(connection,
                    store("prompts", "prompts_idx", Map.of(), Map.of()),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));

            assertThat(documents.requireCapability(VectorScoreSemantics.class).descriptors()).hasSize(1);
            assertThat(documents.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThat(prompts.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThatThrownBy(() -> documents.service().searchByText(
                    "prompts", "query", 3, SearchOptions.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this Redis Store");
        } finally {
            connection.close();
        }
    }

    @Test
    void exposesTypedFilterCompilerAndEscapesAclLiteral() {
        VectorConnectionHandle connection = factory.openConnection(connection(Map.of()));
        try {
            var handle = factory.createStore(connection, store(),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            String compiled = handle.requireCapability(VectorFilterCompiler.class)
                    .compile(new VectorFilter.Eq("tenantId", "tenant'1"));
            assertThat(compiled).isEqualTo("tenantId == 'tenant\\'1'");
        } finally {
            connection.close();
        }
    }

    @Test
    void registersFactoryWithoutCreatingLegacyClientOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(RedisClient.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("redis"), VectorProvider.REDIS, settings);
    }

    private static VectorStoreDefinition store() {
        return store("documents", "documents_idx", Map.of("metadata-fields", "tenantId:tag"), Map.of());
    }

    private static VectorStoreDefinition store(
            String logicalIndex,
            String physicalIndex,
            Map<String, Object> additionalFields,
            Map<String, Object> indexParams) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                logicalIndex, physicalIndex, 4, "cosine", "hnsw", 1, 1,
                additionalFields, indexParams);
        return new VectorStoreDefinition(
                VectorStoreId.of(logicalIndex), VectorConnectionId.of("redis"), "model",
                logicalIndex, true, Set.of(), Map.of(logicalIndex, index));
    }

    private static EmbeddingModel embeddingModel(int dimensions) {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(), new Class<?>[]{EmbeddingModel.class},
                (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return dimensions;
                    if ("toString".equals(method.getName())) return "FakeEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
