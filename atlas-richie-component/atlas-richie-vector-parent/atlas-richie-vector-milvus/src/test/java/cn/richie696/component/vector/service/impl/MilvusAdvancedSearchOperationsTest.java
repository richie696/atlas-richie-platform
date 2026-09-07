/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.query.VectorConsistencyPreference;
import cn.richie696.component.vector.query.VectorParameterDisposition;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorQueryValidationException;
import cn.richie696.component.vector.query.VectorDiversificationOptions;
import cn.richie696.component.vector.query.milvus.MilvusQueryOptions;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusAdvancedSearchOperationsTest {

    @Test
    void appliesTypedHnswEfAndReturnsSanitizedEvidence() {
        MilvusVectorServiceImpl service = mock(MilvusVectorServiceImpl.class);
        when(service.searchByText(eq("docs"), eq("synthetic query"), eq(20), any()))
                .thenReturn(List.of(
                        VectorSearchResult.of("one", "one", 0.9),
                        VectorSearchResult.of("two", "two", 0.8)));
        MilvusAdvancedSearchOperations operations = new MilvusAdvancedSearchOperations(
                service, VectorStoreId.of("milvus-store"), "docs", "hnsw", null, null);

        var execution = operations.search(request(1, 20, new MilvusQueryOptions(64, null)));

        assertThat(execution.results()).extracting(VectorSearchResult::getId).containsExactly("one");
        assertThat(execution.receipt().parameters())
                .anySatisfy(parameter -> {
                    assertThat(parameter.name()).isEqualTo("milvus.ef");
                    assertThat(parameter.applied()).isEqualTo("64");
                    assertThat(parameter.disposition()).isEqualTo(VectorParameterDisposition.APPLIED);
                });
        assertThat(execution.receipt().toString()).doesNotContain("synthetic query").doesNotContain("docs");

        ArgumentCaptor<SearchOptions> options = ArgumentCaptor.forClass(SearchOptions.class);
        verify(service).searchByText(eq("docs"), eq("synthetic query"), eq(20), options.capture());
        assertThat(options.getValue().getProviderSearchParameters()).containsEntry("milvus.ef", 64);
    }

    @Test
    void usesSafeDefaultsWithoutProviderParameters() {
        MilvusVectorServiceImpl service = mock(MilvusVectorServiceImpl.class);
        when(service.searchByText(any(), any(), eq(10), any(SearchOptions.class))).thenReturn(List.of());
        MilvusAdvancedSearchOperations operations = new MilvusAdvancedSearchOperations(
                service, VectorStoreId.of("milvus-store"), "docs", "hnsw", null, null);

        operations.search(VectorQueryRequest.of("synthetic query"));

        ArgumentCaptor<SearchOptions> options = ArgumentCaptor.forClass(SearchOptions.class);
        verify(service).searchByText(eq("docs"), eq("synthetic query"), eq(10), options.capture());
        assertThat(options.getValue().getProviderSearchParameters()).isEmpty();
    }

    @Test
    void rejectsInapplicableOrConflictingOptionsWithStableCodes() {
        MilvusVectorServiceImpl service = mock(MilvusVectorServiceImpl.class);
        MilvusAdvancedSearchOperations hnsw = new MilvusAdvancedSearchOperations(
                service, VectorStoreId.of("milvus-store"), "docs", "hnsw", null, null);

        assertThatThrownBy(() -> hnsw.search(request(5, 10, new MilvusQueryOptions(null, 8))))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.UNSUPPORTED_OPTION));
        assertThatThrownBy(() -> new MilvusQueryOptions(64, 8))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.CONFLICTING_OPTIONS));
    }

    @Test
    void requestsCandidateVectorsOnlyForMmrAndUsesTheCoreDiversifier() {
        MilvusVectorServiceImpl service = mock(MilvusVectorServiceImpl.class);
        when(service.searchByText(eq("docs"), eq("synthetic query"), eq(3), any()))
                .thenReturn(List.of(
                        VectorSearchResult.of("a", "a", 1.0, new float[]{1.0f, 0.0f}),
                        VectorSearchResult.of("b", "b", 0.95, new float[]{0.99f, 0.01f}),
                        VectorSearchResult.of("c", "c", 0.8, new float[]{0.0f, 1.0f})));
        MilvusAdvancedSearchOperations operations = new MilvusAdvancedSearchOperations(
                service, VectorStoreId.of("milvus-store"), "docs", "hnsw", null, null);

        var execution = operations.search(new VectorQueryRequest(
                "synthetic query", 2, null, null, Set.of(), 3, null,
                VectorConsistencyPreference.PROVIDER_DEFAULT,
                new VectorDiversificationOptions(false, true, 0.5D), null));

        assertThat(execution.results()).extracting(VectorSearchResult::getId).containsExactly("a", "c");
        assertThat(execution.results()).allSatisfy(result -> assertThat(result.getVector()).isNull());
        ArgumentCaptor<SearchOptions> options = ArgumentCaptor.forClass(SearchOptions.class);
        verify(service).searchByText(eq("docs"), eq("synthetic query"), eq(3), options.capture());
        assertThat(options.getValue().getIncludeCandidateVectors()).isTrue();
    }

    private static VectorQueryRequest request(int topK, int candidates, MilvusQueryOptions options) {
        return new VectorQueryRequest(
                "synthetic query", topK, null, null, Set.of(), candidates,
                null, VectorConsistencyPreference.PROVIDER_DEFAULT, options);
    }
}
