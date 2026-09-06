package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreId;
import com.volcengine.vikingdb.VikingdbApi;
import com.volcengine.vikingdb.runtime.vector.service.VectorService;
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

class VikingDbVectorProviderFactoryTest {

    private final VikingDbVectorProviderFactory factory = new VikingDbVectorProviderFactory(null);

    @Test
    void validatesStrictConnectionAndProviderSchemaSettings() {
        factory.validateConnection(connection());
        factory.validateStore(connection(), store());

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of(
                "host", "viking.example", "access-key", "ak", "secret-key", "sk", "scheem", "HTTPS"))))
                .hasMessage("unknown VikingDB connection settings: [scheem]");
        assertThatThrownBy(() -> factory.validateStore(connection(), store(
                Map.of("metadata-fields", "tenantId:string", "scalar-index", "principalId"))))
                .hasMessage("Every VikingDB scalar-index field must be declared in metadata-fields");
    }

    @Test
    void advertisesAclFilterOnlyWhenScalarIndexIsConfigured() {
        var capabilities = factory.capabilities(connection(), store());
        assertThat(capabilities.ids()).contains("QUERY_TUNING", "SCORE_STAGES", "PRECOMPUTED_VECTOR",
                "NATIVE_FILTER", "ACL_FILTER", "INDEX_LIFECYCLE");
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
        assertThat(factory.capabilities(connection(), store(Map.of())).ids())
                .contains("QUERY_TUNING", "SCORE_STAGES", "PRECOMPUTED_VECTOR", "INDEX_LIFECYCLE")
                .doesNotContain("NATIVE_FILTER", "ACL_FILTER");
    }

    @Test
    void createsTwoStoreBoundHandlesOnOneConnection() {
        boolean previous = skipSdkConnectivityCheck();
        try {
            VectorConnectionHandle connection = factory.openConnection(connection());
            try {
                var documents = factory.createStore(connection, store(),
                        VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
                var prompts = factory.createStore(connection,
                        store("prompts", "prompts_collection", Map.of()),
                        VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
                assertThatThrownBy(() -> documents.service().searchByText(
                        "prompts", "query", 3, SearchOptions.builder().build()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("logicalIndex=documents");
                assertThat(prompts.id().value()).isEqualTo("prompts");
            } finally {
                connection.close();
            }
        } finally {
            setSdkConnectivityCheck(previous);
        }
    }

    @Test
    void exposesTypedAclFilterCompilerWithoutClaimingHybrid() {
        boolean previous = skipSdkConnectivityCheck();
        try {
            VectorConnectionHandle connection = factory.openConnection(connection());
            try {
                var handle = factory.createStore(connection, store(),
                        VectorEmbeddingModelBinding.of("model", embeddingModel(4)));
                assertThat(handle.requireCapability(VectorFilterCompiler.class)
                        .compile(new VectorFilter.Eq("tenantId", "tenant-1")))
                        .isEqualTo("tenantId == 'tenant-1'");
                assertThat(handle.storeCapabilities().supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
                assertThat(handle.capability(VectorAdvancedSearchOperations.class)).isPresent();
                assertThat(handle.capability(cn.richie696.ai.vectorstore.vikingdb.api.VikingDbSearchOperations.class))
                        .isPresent();
            } finally {
                connection.close();
            }
        } finally {
            setSdkConnectivityCheck(previous);
        }
    }

    @Test
    void registersFactoryWithoutCreatingLegacyClientsOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(VikingDbVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(VikingDbVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(VectorService.class);
                    assertThat(context).doesNotHaveBean(VikingdbApi.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    private static VectorConnectionDefinition connection() {
        return connection(Map.of(
                "host", "viking.example", "control-endpoint", "https://control.example",
                "region", "cn-beijing", "access-key", "ak", "secret-key", "sk", "scheme", "HTTPS"));
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("vikingdb"), VectorProvider.VIKINGDB, settings);
    }

    private static VectorStoreDefinition store() {
        return store(Map.of(
                "metadata-fields", "tenantId:string,principalId:string",
                "scalar-index", "tenantId,principalId"));
    }

    private static VectorStoreDefinition store(Map<String, Object> additionalFields) {
        return store("documents", "documents_collection", additionalFields);
    }

    private static VectorStoreDefinition store(
            String logicalIndex, String physicalCollection, Map<String, Object> additionalFields) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                logicalIndex, physicalCollection, 4, "cosine", "hnsw", 1, 1,
                additionalFields, Map.of());
        return new VectorStoreDefinition(
                VectorStoreId.of(logicalIndex), VectorConnectionId.of("vikingdb"), "model",
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

    private static boolean skipSdkConnectivityCheck() {
        try {
            var field = VectorService.class.getDeclaredField("isServiceConnectable");
            field.setAccessible(true);
            boolean previous = field.getBoolean(null);
            field.setBoolean(null, true);
            return previous;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to isolate VikingDB SDK connectivity check", exception);
        }
    }

    private static void setSdkConnectivityCheck(boolean value) {
        try {
            var field = VectorService.class.getDeclaredField("isServiceConnectable");
            field.setAccessible(true);
            field.setBoolean(null, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to restore VikingDB SDK connectivity check", exception);
        }
    }
}
