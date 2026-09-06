/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import io.weaviate.client.WeaviateClient;
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

class WeaviateVectorProviderFactoryTest {

    private final WeaviateVectorProviderFactory factory = new WeaviateVectorProviderFactory(null);

    @Test
    void validatesStrictConnectionAndStoreSettings() {
        factory.validateConnection(connection(Map.of("host", "weaviate.internal:8080", "scheme", "https")));
        factory.validateStore(connection(Map.of()), store(Map.of(
                "consistency-level", "quorum",
                "filter-metadata-fields", "tenantId:text,priority:number")));

        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("api-keey", "secret"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown Weaviate connection settings: [api-keey]");
        assertThatThrownBy(() -> factory.validateConnection(connection(Map.of("scheme", "ftp"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Weaviate scheme must be http or https");
        assertThatThrownBy(() -> factory.validateStore(connection(Map.of()), storeWithClass("lowercase")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Weaviate class name");
    }

    @Test
    void advertisesAclSafeHybridOnlyBecauseTypedEntryPointIsExposed() {
        var capabilities = factory.capabilities(connection(Map.of()), store(Map.of()));

        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "ACL_FILTER", "ACL_SAFE_HYBRID", "QUERY_TUNING",
                "SCORE_STAGES", "INDEX_LIFECYCLE");
        assertThat(capabilities.supports(VectorCapability.CANDIDATE_VECTOR)).isFalse();
        assertThat(capabilities.supports(VectorCapability.SCORE_STAGES)).isTrue();
    }

    @Test
    void createsStoreBoundAclHybridHandleAndRejectsOtherClassesBeforeNetworkIo() {
        VectorConnectionHandle connection = factory.openConnection(connection(Map.of()));
        VectorEmbeddingModelBinding model = VectorEmbeddingModelBinding.of("knowledgeModel", embeddingModel(3));
        VectorStoreHandle handle = factory.createStore(connection, store(Map.of()), model);

        assertThat(handle.requireCapability(VectorScoreSemantics.class).descriptors()).hasSize(1);
        assertThat(handle.requireCapability(VectorFilterCompiler.class)).isNotNull();
        VectorAclAwareHybridSearchOperations aclHybrid =
                handle.requireCapability(VectorAclAwareHybridSearchOperations.class);
        assertThatThrownBy(() -> aclHybrid.hybridSearch(
                "other",
                "query",
                "keyword",
                5,
                HybridSearchOptions.builder().build(),
                VectorFilter.eq("tenantId", "tenant-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not declared by this Weaviate Store");
    }

    @Test
    void registersFactoryWithoutCreatingLegacyClientOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WeaviateVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(WeaviateVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(WeaviateClient.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void namedSpringTopologyCreatesAclSafeRegistryHandleAndSingleStoreCompatibilityService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WeaviateVectorAutoConfiguration.class,
                        VectorAutoConfiguration.class))
                .withBean("aiEmbeddingModel", EmbeddingModel.class, () -> embeddingModel(3))
                .withPropertyValues(
                        "platform.component.vector.connections.weaviate.provider=weaviate",
                        "platform.component.vector.connections.weaviate.settings.host=localhost:8080",
                        "platform.component.vector.stores.knowledge.connection-ref=weaviate",
                        "platform.component.vector.stores.knowledge.embedding-model-ref=aiEmbeddingModel",
                        "platform.component.vector.stores.knowledge.default-index=chunks",
                        "platform.component.vector.stores.knowledge.required-capabilities[0]=ACL_SAFE_HYBRID",
                        "platform.component.vector.stores.knowledge.indexes.chunks.name=KnowledgeChunks",
                        "platform.component.vector.stores.knowledge.indexes.chunks.dimension=3",
                        "platform.component.vector.stores.knowledge.indexes.chunks.metric=cosine",
                        "platform.component.vector.stores.knowledge.indexes.chunks.index-type=hnsw")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VectorStoreHandle handle = context.getBean(VectorServiceRegistry.class)
                            .require(VectorStoreId.of("knowledge"));
                    assertThat(handle.storeCapabilities().supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
                    assertThat(handle.requireCapability(VectorAclAwareHybridSearchOperations.class)).isNotNull();
                    assertThat(context.getBean(VectorService.class)).isSameAs(handle.service());
                });
    }

    private static VectorConnectionDefinition connection(Map<String, Object> settings) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of("weaviate"), VectorProvider.WEAVIATE, settings);
    }

    private static VectorStoreDefinition store(Map<String, Object> additionalFields) {
        return storeWithClass("KnowledgeChunks", additionalFields);
    }

    private static VectorStoreDefinition storeWithClass(String className) {
        return storeWithClass(className, Map.of());
    }

    private static VectorStoreDefinition storeWithClass(
            String className,
            Map<String, Object> additionalFields) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                "chunks", className, 3, "cosine", "hnsw", 1, 1, additionalFields, Map.of());
        return new VectorStoreDefinition(
                VectorStoreId.of("knowledge"),
                VectorConnectionId.of("weaviate"),
                "knowledgeModel",
                "chunks",
                true,
                Set.of("ACL_SAFE_HYBRID"),
                Map.of("chunks", index));
    }

    private static EmbeddingModel embeddingModel(int dimensions) {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
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
