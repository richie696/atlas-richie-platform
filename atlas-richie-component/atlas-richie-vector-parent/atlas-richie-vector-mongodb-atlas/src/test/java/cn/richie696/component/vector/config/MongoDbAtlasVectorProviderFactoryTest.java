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
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoDbAtlasVectorProviderFactoryTest {

    private final AtomicBoolean clientClosed = new AtomicBoolean();
    private final MongoDbAtlasVectorProviderFactory factory =
            new MongoDbAtlasVectorProviderFactory(null, ignored -> mongoClient(clientClosed));

    @Test
    void validatesStrictConnectionStoreAndAppliedCandidateSetting() {
        factory.validateConnection(connection(Map.of(
                "connection-string", "mongodb://localhost:27017", "database", "vectors")));
        factory.validateStore(connection(), store("documents", "vector_documents",
                Map.of("filter-metadata-fields", "tenantId,principalId"),
                Map.of("numCandidates", 400)));

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of(
                "connection-string", "https://localhost", "database", "vectors"))))
                .hasMessageContaining("must use mongodb");
        assertThatThrownBy(() -> factory.validateStore(connection(), store(
                "documents", "vector_documents", Map.of(), Map.of("efSearch", 30))))
                .hasMessage("unknown MongoDB Atlas index-params: [efSearch]");
        assertThatThrownBy(() -> factory.validateStore(connection(), store(
                "documents", "vector_documents", Map.of("filter-metadata-fields", "tenant-id"), Map.of())))
                .hasMessage("invalid MongoDB Atlas filter metadata field: tenant-id");
    }

    @Test
    void advertisesOnlyImplementedNamedCapabilities() {
        var capabilities = factory.capabilities(connection(), store());
        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "ACL_FILTER", "SCORE_STAGES", "INDEX_LIFECYCLE");
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
        assertThat(capabilities.supports(VectorCapability.QUERY_TUNING)).isFalse();
        assertThat(factory.capabilities(connection(),
                store("plain", "vector_plain", Map.of(), Map.of())).ids())
                .containsExactlyInAnyOrder("SCORE_STAGES", "INDEX_LIFECYCLE");
    }

    @Test
    void advertisesAclSafeHybridOnlyWhenAtlasFilterFieldsAreDeclared() {
        var hybrid = store("documents", "vector_documents", Map.of(
                "hybrid-enabled", true, "filter-metadata-fields", "tenantId"), Map.of());
        assertThat(factory.capabilities(connection(), hybrid).supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
        assertThatThrownBy(() -> factory.validateStore(connection(), store("documents", "vector_documents", Map.of("hybrid-enabled", true), Map.of())))
                .hasMessageContaining("filter-metadata-fields");
    }

    @Test
    void mapsEveryAclMetadataFieldAsANestedAtlasSearchToken() {
        org.bson.Document definition = MongoDbAtlasVectorProviderFactory.textSearchIndexDefinition(
                "text_documents", java.util.List.of("tenantId", "principalId"));

        org.bson.Document mappings = definition.get("definition", org.bson.Document.class)
                .get("mappings", org.bson.Document.class);
        org.bson.Document metadata = mappings.get("fields", org.bson.Document.class)
                .get("metadata", org.bson.Document.class);
        org.bson.Document fields = metadata.get("fields", org.bson.Document.class);
        assertThat(metadata.getString("type")).isEqualTo("document");
        assertThat(fields.get("tenantId", org.bson.Document.class).getString("type")).isEqualTo("token");
        assertThat(fields.get("principalId", org.bson.Document.class).getString("type")).isEqualTo("token");
    }

    @Test
    void createsTwoStoreBoundHandlesOnOneConnectionWithoutNetworkAccess() {
        VectorConnectionHandle connection = factory.openConnection(connection());
        try {
            var documents = factory.createStore(connection, store(),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            var prompts = factory.createStore(connection,
                    store("prompts", "vector_prompts", Map.of(), Map.of()),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));

            assertThat(documents.requireCapability(VectorScoreSemantics.class).descriptors()).hasSize(1);
            assertThat(documents.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThat(prompts.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThatThrownBy(() -> documents.service().searchByText(
                    "prompts", "query", 3, SearchOptions.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this MongoDB Atlas Store");
        } finally {
            connection.close();
        }
        assertThat(clientClosed).isTrue();
    }

    @Test
    void exposesTypedFilterCompilerForAclFields() {
        VectorConnectionHandle connection = factory.openConnection(connection());
        try {
            var handle = factory.createStore(connection, store(),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            String compiled = handle.requireCapability(VectorFilterCompiler.class)
                    .compile(new VectorFilter.Eq("tenantId", "tenant-1"));
            assertThat(compiled).isEqualTo("tenantId == 'tenant-1'");
        } finally {
            connection.close();
        }
    }

    @Test
    void registersFactoryWithoutCreatingLegacyClientOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoDbAtlasVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(MongoDbAtlasVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(MongoClient.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    private static VectorConnectionDefinition connection() {
        return connection(Map.of("connection-string", "mongodb://localhost:27017", "database", "vectors"));
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("mongodb"), VectorProvider.MONGODB, settings);
    }

    private static VectorStoreDefinition store() {
        return store("documents", "vector_documents", Map.of("filter-metadata-fields", "tenantId"), Map.of());
    }

    private static VectorStoreDefinition store(
            String logicalIndex,
            String physicalCollection,
            Map<String, Object> additionalFields,
            Map<String, Object> indexParams) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                logicalIndex, physicalCollection, 4, "cosine", "hnsw", 1, 1,
                additionalFields, indexParams);
        return new VectorStoreDefinition(
                VectorStoreId.of(logicalIndex), VectorConnectionId.of("mongodb"), "model",
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

    private static MongoClient mongoClient(AtomicBoolean closed) {
        return MongoClient.class.cast(Proxy.newProxyInstance(
                MongoClient.class.getClassLoader(), new Class<?>[]{MongoClient.class},
                (proxy, method, arguments) -> {
                    if ("close".equals(method.getName())) {
                        closed.set(true);
                        return null;
                    }
                    if ("toString".equals(method.getName())) return "FakeMongoClient";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
