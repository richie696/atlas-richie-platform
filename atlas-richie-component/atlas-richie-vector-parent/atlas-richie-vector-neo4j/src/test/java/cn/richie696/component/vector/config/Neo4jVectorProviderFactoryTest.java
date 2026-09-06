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
import org.neo4j.driver.Driver;
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

class Neo4jVectorProviderFactoryTest {

    private final AtomicBoolean driverClosed = new AtomicBoolean();
    private final Neo4jVectorProviderFactory factory =
            new Neo4jVectorProviderFactory(null, ignored -> driver(driverClosed));

    @Test
    void validatesStrictConnectionAndOnlyAppliedIndexSettings() {
        factory.validateConnection(connection(Map.of(
                "uri", "neo4j+s://graph.example:7687", "username", "neo4j", "password", "secret")));
        factory.validateStore(connection(), store());

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("username", "neo4j"))))
                .hasMessage("Neo4j username and password must be configured together");
        assertThatThrownBy(() -> factory.validateStore(connection(), store(Map.of("M", 16))))
                .hasMessageContaining("must be empty");
    }

    @Test
    void marksPostCandidateFilterWithoutClaimingAclSafety() {
        var capabilities = factory.capabilities(connection(), store());
        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "SCORE_STAGES", "INDEX_LIFECYCLE");
        assertThat(capabilities.supports(VectorCapability.ACL_FILTER)).isFalse();
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
    }

    @Test
    void createsTwoStoreBoundHandlesAndClosesSharedDriver() {
        VectorConnectionHandle connection = factory.openConnection(connection());
        try {
            var documents = factory.createStore(connection, store(),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            var prompts = factory.createStore(connection, store("prompts", "prompts"),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            assertThat(documents.requireCapability(VectorScoreSemantics.class).descriptors()).hasSize(1);
            assertThat(documents.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThat(prompts.requireCapability(VectorIndexLifecycleOperations.class)).isNotNull();
            assertThatThrownBy(() -> documents.service().searchByText(
                    "prompts", "query", 3, SearchOptions.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this Neo4j Store");
        } finally {
            connection.close();
        }
        assertThat(driverClosed).isTrue();
    }

    @Test
    void exposesTypedProviderFilterButNotAclFilterCapability() {
        VectorConnectionHandle connection = factory.openConnection(connection());
        try {
            var handle = factory.createStore(connection, store(),
                    VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
            assertThat(handle.requireCapability(VectorFilterCompiler.class)
                    .compile(new VectorFilter.Eq("tenantId", "tenant-1")))
                    .isEqualTo("tenantId == 'tenant-1'");
            assertThat(handle.storeCapabilities().supports(VectorCapability.ACL_FILTER)).isFalse();
        } finally {
            connection.close();
        }
    }

    @Test
    void registersFactoryWithoutCreatingLegacyDriverOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Neo4jVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(Neo4jVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(Driver.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    private static VectorConnectionDefinition connection() {
        return connection(Map.of("uri", "bolt://localhost:7687", "database", "neo4j"));
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("neo4j"), VectorProvider.NEO4J, settings);
    }

    private static VectorStoreDefinition store() {
        return store("documents", "documents");
    }

    private static VectorStoreDefinition store(Map<String, Object> indexParams) {
        return store("documents", "documents", indexParams);
    }

    private static VectorStoreDefinition store(String logicalIndex, String physicalIndex) {
        return store(logicalIndex, physicalIndex, Map.of());
    }

    private static VectorStoreDefinition store(
            String logicalIndex, String physicalIndex, Map<String, Object> indexParams) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                logicalIndex, physicalIndex, 4, "cosine", "hnsw", 1, 1, Map.of(), indexParams);
        return new VectorStoreDefinition(
                VectorStoreId.of(logicalIndex), VectorConnectionId.of("neo4j"), "model",
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

    private static Driver driver(AtomicBoolean closed) {
        return Driver.class.cast(Proxy.newProxyInstance(
                Driver.class.getClassLoader(), new Class<?>[]{Driver.class},
                (proxy, method, arguments) -> {
                    if ("close".equals(method.getName())) {
                        closed.set(true);
                        return null;
                    }
                    if ("toString".equals(method.getName())) return "FakeNeo4jDriver";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
