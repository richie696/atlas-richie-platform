/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.query.VectorDiversificationOptions;
import cn.richie696.component.vector.query.VectorParameterDisposition;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorQueryValidationException;
import cn.richie696.component.vector.query.postgresql.PostgresqlQueryOptions;
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

class PostgresqlAdvancedSearchOperationsTest {

    @Test
    void appliesTypedHnswEfSearchToTransactionScopedDataPlane() {
        PostgresqlVectorServiceImpl service = mock(PostgresqlVectorServiceImpl.class);
        when(service.searchByText(eq("docs"), eq("synthetic query"), eq(30), any()))
                .thenReturn(List.of(VectorSearchResult.of("one", "one", 0.9)));
        PostgresqlAdvancedSearchOperations operations = new PostgresqlAdvancedSearchOperations(
                service, VectorStoreId.of("pg-store"), "docs", "hnsw", null, null);

        var execution = operations.search(request(new PostgresqlQueryOptions(80, null)));

        assertThat(execution.results()).extracting(VectorSearchResult::getId).containsExactly("one");
        assertThat(execution.receipt().parameters()).anySatisfy(parameter -> {
            assertThat(parameter.name()).isEqualTo("pgvector.efSearch");
            assertThat(parameter.applied()).isEqualTo("80");
            assertThat(parameter.disposition()).isEqualTo(VectorParameterDisposition.APPLIED);
        });
        ArgumentCaptor<SearchOptions> options = ArgumentCaptor.forClass(SearchOptions.class);
        verify(service).searchByText(eq("docs"), eq("synthetic query"), eq(30), options.capture());
        assertThat(options.getValue().getProviderSearchParameters()).containsEntry("pgvector.efSearch", 80);
    }

    @Test
    void rejectsWrongIndexFamilyAndUnsafeValues() {
        PostgresqlVectorServiceImpl service = mock(PostgresqlVectorServiceImpl.class);
        PostgresqlAdvancedSearchOperations hnsw = new PostgresqlAdvancedSearchOperations(
                service, VectorStoreId.of("pg-store"), "docs", "hnsw", null, null);

        assertThatThrownBy(() -> hnsw.search(request(new PostgresqlQueryOptions(null, 5))))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.UNSUPPORTED_OPTION));
        assertThatThrownBy(() -> new PostgresqlQueryOptions(0, null))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.INVALID_VALUE));
    }

    @Test
    void fetchesCandidateVectorsOnlyWhenExplicitlyRequestedAndAppliesMmr() {
        PostgresqlVectorServiceImpl service = mock(PostgresqlVectorServiceImpl.class);
        when(service.searchCandidatesByText(eq("docs"), eq("synthetic query"), eq(30), any(), eq(true)))
                .thenReturn(List.of(
                        VectorSearchResult.of("a", "a", 1.0, new float[]{1.0f, 0.0f}),
                        VectorSearchResult.of("b", "b", 0.95, new float[]{0.99f, 0.01f}),
                        VectorSearchResult.of("c", "c", 0.8, new float[]{0.0f, 1.0f})));
        PostgresqlAdvancedSearchOperations operations = new PostgresqlAdvancedSearchOperations(
                service, VectorStoreId.of("pg-store"), "docs", "hnsw", null, null);
        VectorQueryRequest request = new VectorQueryRequest(
                "synthetic query", 2, null, null, Set.of(), 30, null, null,
                new VectorDiversificationOptions(false, true, 0.5D), null);

        var execution = operations.search(request);

        assertThat(execution.results()).extracting(VectorSearchResult::getId).containsExactly("a", "c");
        assertThat(execution.results()).allSatisfy(result -> assertThat(result.getVector()).isNull());
        assertThat(execution.receipt().parameters()).anySatisfy(parameter -> {
            assertThat(parameter.name()).isEqualTo("mmrLambda");
            assertThat(parameter.applied()).isEqualTo("0.5");
        });
        verify(service).searchCandidatesByText(eq("docs"), eq("synthetic query"), eq(30), any(), eq(true));
    }

    private static VectorQueryRequest request(PostgresqlQueryOptions options) {
        return new VectorQueryRequest(
                "synthetic query", 5, null, null, Set.of(), 30, null, null, options);
    }
}
