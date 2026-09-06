/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.WeaviateVectorFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeaviateAclSafeHybridHttpTest {

    @Test
    void aclFilterAndHybridBranchesAreSentInOneGraphqlRequest() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/graphql", exchange -> {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{\"Get\":{\"KnowledgeChunks\":[]}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            WeaviateClient client = new WeaviateClient(new Config(
                    "http", "127.0.0.1:" + server.getAddress().getPort()));
            WeaviateVectorServiceImpl service = new WeaviateVectorServiceImpl(
                    null,
                    proxy(VectorStore.class),
                    proxy(EmbeddingModel.class),
                    client,
                    new WeaviateVectorFilterCompiler(),
                    Map.of("chunks", "KnowledgeChunks"));

            assertThat(service.hybridSearch(
                    "chunks",
                    "employee handbook",
                    "leave policy",
                    5,
                    HybridSearchOptions.builder().vectorWeight(0.6).keywordWeight(0.4).build(),
                    VectorFilter.and(
                            VectorFilter.eq("tenantId", "tenant-a"),
                            VectorFilter.in("principalId", java.util.List.of("user-1", "group-2")))))
                    .isEmpty();

            assertThat(requestCount).hasValue(1);
            assertThat(requestBody.get())
                    .contains("KnowledgeChunks", "hybrid", "alpha: 0.6", "where:", "meta_tenantId", "meta_principalId")
                    .contains("tenant-a", "user-1", "group-2", "vector: [0.1,0.2,0.3]");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void isolatesTenantPredicatesBeforeFusionInSeparateSingleRequests() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = server(bodies);
        try {
            WeaviateVectorServiceImpl service = service(server);

            service.hybridSearch("chunks", "query", "keyword", 5, null,
                    VectorFilter.eq("tenantId", "tenant-a"));
            service.hybridSearch("chunks", "query", "keyword", 5, null,
                    VectorFilter.eq("tenantId", "tenant-b"));

            assertThat(bodies).hasSize(2);
            assertThat(bodies.get(0)).contains("hybrid", "where:", "tenant-a").doesNotContain("tenant-b");
            assertThat(bodies.get(1)).contains("hybrid", "where:", "tenant-b").doesNotContain("tenant-a");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMissingEmptyMaliciousAndConflictingAclBeforeProviderCall() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = server(bodies);
        try {
            WeaviateVectorServiceImpl service = service(server);

            assertThatThrownBy(() -> service.hybridSearch(
                    "chunks", "query", "keyword", 5, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
            assertThatThrownBy(() -> VectorFilter.in("principalId", List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.hybridSearch(
                    "chunks", "query", "keyword", 5, null,
                    VectorFilter.eq("tenantId\"]} malicious", "tenant-a")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("illegal Weaviate filter field");

            HybridSearchOptions conflicting = HybridSearchOptions.builder()
                    .searchOptions(cn.richie696.component.vector.model.SearchOptions.builder()
                            .filter(VectorFilter.eq("tenantId", "tenant-b"))
                            .build())
                    .build();
            assertThatThrownBy(() -> service.hybridSearch(
                    "chunks", "query", "keyword", 5, conflicting,
                    VectorFilter.eq("tenantId", "tenant-a")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conflicts");
            assertThat(bodies).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void escapesAclValuesWithoutChangingTheGraphqlStructure() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = server(bodies);
        try {
            WeaviateVectorServiceImpl service = service(server);
            service.hybridSearch("chunks", "query", "keyword", 5, null,
                    VectorFilter.eq("tenantId", "tenant-a\"}\nmutation"));

            assertThat(bodies).hasSize(1);
            String graphql = new ObjectMapper().readTree(bodies.getFirst()).get("query").asText();
            assertThat(graphql).contains("where:", "hybrid", "tenant-a\\\"}\\nmutation");
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(List<String> bodies) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/graphql", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{\"Get\":{\"KnowledgeChunks\":[]}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static WeaviateVectorServiceImpl service(HttpServer server) {
        WeaviateClient client = new WeaviateClient(new Config(
                "http", "127.0.0.1:" + server.getAddress().getPort()));
        return new WeaviateVectorServiceImpl(
                null,
                proxy(VectorStore.class),
                proxy(EmbeddingModel.class),
                client,
                new WeaviateVectorFilterCompiler(),
                Map.of("chunks", "KnowledgeChunks"));
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if ("toString".equals(method.getName())) return "Fake" + type.getSimpleName();
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
                    if ("equals".equals(method.getName())) return instance == arguments[0];
                    if ("embed".equals(method.getName())) return new float[]{0.1f, 0.2f, 0.3f};
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
