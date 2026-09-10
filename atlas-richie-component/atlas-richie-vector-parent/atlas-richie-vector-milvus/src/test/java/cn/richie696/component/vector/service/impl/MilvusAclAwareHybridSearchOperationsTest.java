/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.MilvusVectorFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusAclAwareHybridSearchOperationsTest {

    @Test
    void shouldPushTheSameAclExpressionIntoDenseAndSparseRecall() {
        List<HybridSearchReq> requests = new ArrayList<>();
        SearchResp response = SearchResp.builder().searchResults(List.of(List.of(
                SearchResp.SearchResult.builder().id("allowed").score(0.9F)
                        .entity(Map.of("content", "allowed content", "metadata", "{\"tenantId\":\"tenant-a\"}"))
                        .build()))).build();
        MilvusAclAwareHybridSearchOperations operations = new MilvusAclAwareHybridSearchOperations(
                request -> {
                    requests.add(request);
                    return response;
                }, embeddingModel(), new MilvusVectorFilterCompiler());
        VectorFilter acl = VectorFilter.and(VectorFilter.eq("tenantId", "tenant-a"),
                VectorFilter.in("principalId", List.of("user-1", "group-1")));

        var result = operations.hybridSearch("documents", "semantic query", "keyword query", 5,
                HybridSearchOptions.builder().searchOptions(SearchOptions.builder().filter(acl).build()).build(), acl);

        assertThat(requests).singleElement();
        assertThat(requests.getFirst().getSearchRequests()).hasSize(2);
        assertThat(requests.getFirst().getSearchRequests())
                .allSatisfy(branch -> assertThat(branch.getFilter())
                        .isEqualTo("(tenantId == \"tenant-a\" && principalId in [\"user-1\", \"group-1\"])")
                        .doesNotContain("metadata"));
        assertThat(result).singleElement().satisfies(hit -> {
            assertThat(hit.getId()).isEqualTo("allowed");
            assertThat(hit.getMetadata().toString()).contains("tenantId=tenant-a");
        });
    }

    @Test
    void shouldRejectMissingOrConflictingAclBeforeProviderCall() {
        List<HybridSearchReq> requests = new ArrayList<>();
        MilvusAclAwareHybridSearchOperations operations = new MilvusAclAwareHybridSearchOperations(
                request -> {
                    requests.add(request);
                    throw new AssertionError("provider must not be called for invalid ACL input");
                }, embeddingModel(), new MilvusVectorFilterCompiler());
        VectorFilter acl = VectorFilter.eq("tenantId", "tenant-a");

        assertThatThrownBy(() -> operations.hybridSearch("documents", "query", null, 5,
                HybridSearchOptions.builder().build(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ACL filter");
        assertThatThrownBy(() -> operations.hybridSearch("documents", "query", null, 5,
                HybridSearchOptions.builder().searchOptions(SearchOptions.builder()
                        .filter(VectorFilter.eq("tenantId", "tenant-b")).build()).build(), acl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conflicts");
        assertThat(requests).isEmpty();
    }

    private static EmbeddingModel embeddingModel() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(), new Class<?>[]{EmbeddingModel.class},
                (proxy, method, arguments) -> {
                    if ("embed".equals(method.getName())) return new float[]{1.0F, 0.0F};
                    if ("dimensions".equals(method.getName())) return 2;
                    return null;
                }));
    }
}
