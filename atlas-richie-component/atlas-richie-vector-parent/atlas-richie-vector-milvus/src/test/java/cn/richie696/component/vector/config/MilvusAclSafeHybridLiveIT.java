/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in local acceptance test; it creates and always drops one unique Milvus collection. */
@EnabledIfEnvironmentVariable(named = "VECTOR_MILVUS_IT_RUN", matches = "true")
class MilvusAclSafeHybridLiveIT {

    @Test
    void filtersDenseAndBm25CandidateRecallBeforeHybridFusion() {
        String collection = "atlas_hybrid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MilvusVectorProviderFactory factory = new MilvusVectorProviderFactory(null);
        try (VectorConnectionHandle connection = factory.openConnection(connection())) {
            VectorStoreHandle store = factory.createStore(connection, store(collection), VectorEmbeddingModelBinding.of("live", embeddingModel()));
            VectorIndexLifecycleOperations lifecycle = store.requireCapability(VectorIndexLifecycleOperations.class);
            try {
                lifecycle.createIndex(collection, indexConfig(collection));

                store.service().upsert(VectorRecord.text(collection, "acl lexical marker",
                        Map.of("tenantId", "tenant-a", "principalId", "user-1")).setId("allowed"));
                store.service().upsert(VectorRecord.text(collection, "acl lexical marker",
                        Map.of("tenantId", "tenant-b", "principalId", "user-2")).setId("denied"));

                VectorFilter acl = VectorFilter.and(VectorFilter.eq("tenantId", "tenant-a"),
                        VectorFilter.in("principalId", List.of("user-1", "group-1")));
                VectorAclAwareHybridSearchOperations hybrid = store.requireCapability(VectorAclAwareHybridSearchOperations.class);
                var results = hybrid.hybridSearch(collection, "semantic query", "lexical marker", 10,
                        HybridSearchOptions.builder().vectorWeight(0.5D).keywordWeight(0.5D)
                                .searchOptions(SearchOptions.builder().filter(acl).build()).build(), acl);

                assertThat(store.storeCapabilities().ids()).contains("ACL_SAFE_HYBRID");
                assertThat(results).extracting(hit -> hit.getId()).containsExactly("allowed");
            } finally {
                lifecycle.deleteIndex(collection);
            }
        }
    }

    private static VectorConnectionDefinition connection() {
        return new VectorConnectionDefinition(VectorConnectionId.of("milvus-live"), VectorProvider.MILVUS,
                Map.of("host", "localhost", "port", 19530, "username", "root", "password", "Milvus"));
    }

    private static VectorStoreDefinition store(String collection) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("hybrid-enabled", true);
        fields.put("tenantId", Map.of("data_type", "VarChar"));
        fields.put("principalId", Map.of("data_type", "VarChar"));
        VectorIndexDefinition index = new VectorIndexDefinition(collection, collection, 4, "cosine", "hnsw", 1, 1,
                fields, Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("milvus-live"), VectorConnectionId.of("milvus-live"),
                "live", collection, true, Set.of(), Map.of(collection, index));
    }

    private static VectorProperties.IndexConfig indexConfig(String collection) {
        VectorProperties.IndexConfig index = new VectorProperties.IndexConfig();
        index.setName(collection);
        index.setDimension(4);
        index.setMetric("cosine");
        index.setIndexType("hnsw");
        index.setShards(1);
        index.setAdditionalFields(store(collection).indexes().get(collection).additionalFields());
        return index;
    }

    private static EmbeddingModel embeddingModel() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class}, (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return 4;
                    if ("embed".equals(method.getName())) return new float[]{1.0F, 0.0F, 0.0F, 0.0F};
                    if ("toString".equals(method.getName())) return "MilvusAclHybridLiveEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
