/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.filter.QdrantVectorFilterCompiler;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.impl.QdRantVectorServiceImpl;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VECTOR_QDRANT_IT_RUN", matches = "true")
class QdrantNativeFilterLiveIT {

    @Test
    void filtersTenantAndPrincipalBeforeNativeVectorRecall() throws Exception {
        String collection = "atlas_acl_it_" + UUID.randomUUID().toString().replace("-", "");
        try (QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder("127.0.0.1", 6334, false).build())) {
            client.createCollectionAsync(Collections.CreateCollection.newBuilder()
                            .setCollectionName(collection)
                            .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                                    .setParams(Collections.VectorParams.newBuilder()
                                            .setSize(4)
                                            .setDistance(Collections.Distance.Cosine)))
                            .build())
                    .get(10, TimeUnit.SECONDS);
            try {
                EmbeddingModel embedding = embeddingModel();
                QdrantVectorStore vectorStore = QdrantVectorStore.builder(client, embedding)
                        .collectionName(collection)
                        .build();
                QdRantVectorServiceImpl service = new QdRantVectorServiceImpl(
                        null, vectorStore, embedding, client, Map.of("documents", collection));
                service.setVectorFilterCompiler(new QdrantVectorFilterCompiler());

                String allowedId = UUID.randomUUID().toString();
                String deniedId = UUID.randomUUID().toString();
                vectorStore.add(List.of(
                        Document.builder().id(allowedId).text("shared text")
                                .metadata(Map.of("tenantId", "tenant-a", "principalId", "user-1")).build(),
                        Document.builder().id(deniedId).text("shared text")
                                .metadata(Map.of("tenantId", "tenant-b", "principalId", "user-2")).build()));

                var results = service.searchByText(
                        "documents",
                        "shared text",
                        10,
                        SearchOptions.builder()
                                .rerank(false)
                                .filter(VectorFilter.and(
                                        VectorFilter.eq("tenantId", "tenant-a"),
                                        VectorFilter.in("principalId", List.of("user-1", "group-1"))))
                                .build());

                assertThat(results).extracting(result -> result.getId()).containsExactly(allowedId);
                assertThat(results).noneMatch(result -> deniedId.equals(result.getId()));
            } finally {
                client.deleteCollectionAsync(collection).get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static EmbeddingModel embeddingModel() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return 4;
                    if ("embed".equals(method.getName()) && arguments[0] instanceof List<?> values) {
                        return values.stream().map(ignored -> new float[]{1.0f, 0.0f, 0.0f, 0.0f}).toList();
                    }
                    if ("embed".equals(method.getName())) return new float[]{1.0f, 0.0f, 0.0f, 0.0f};
                    if ("toString".equals(method.getName())) return "DeterministicEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
