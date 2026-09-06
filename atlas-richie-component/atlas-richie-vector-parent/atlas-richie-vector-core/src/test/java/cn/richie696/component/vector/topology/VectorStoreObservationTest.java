/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.diagnostics.VectorErrorCategory;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.exceptions.VectorProviderUnavailableException;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.query.VectorDiversificationOptions;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorSearchExecution;
import cn.richie696.component.vector.query.VectorSearchExecutionReceipt;
import cn.richie696.component.vector.observation.VectorStoreObservationEvent;
import cn.richie696.component.vector.observation.VectorStoreOperation;
import cn.richie696.component.vector.observation.VectorStoreOperationResult;
import cn.richie696.component.vector.service.VectorIndexStatsOperations;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreObservationTest {

    @Test
    void shouldEmitBoundedMetricTagsAndSanitizedTraceAttributes() {
        List<VectorStoreObservationEvent> events = new ArrayList<>();
        VectorStoreHandle observed = handle(service()).observed(events::add);

        observed.service().searchByText(
                "logical-index", "private query body", 10, SearchOptions.builder().rerank(false).build());

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.operation()).isEqualTo(VectorStoreOperation.SEARCH_TEXT);
            assertThat(event.result()).isEqualTo(VectorStoreOperationResult.SUCCESS);
            assertThat(event.metricTags()).containsOnlyKeys("store", "provider", "operation", "result");
            assertThat(event.traceAttributes()).containsKeys(
                    "vector.store", "vector.provider", "vector.operation", "vector.result",
                    "vector.index.fingerprint", "vector.capabilities", "vector.error.category");
            assertThat(event.indexFingerprint()).hasSize(16).isNotEqualTo("logical-index");
            assertThat(event.toString()).doesNotContain("private query body");
        });
    }

    @Test
    void shouldClassifyFailuresAndPreserveOriginalException() {
        List<VectorStoreObservationEvent> events = new ArrayList<>();
        VectorStoreHandle observed = handle(service()).observed(events::add);

        assertThatThrownBy(() -> observed.service().deleteById("logical-index", "id-1"))
                .isInstanceOf(VectorProviderUnavailableException.class)
                .hasMessage("provider unavailable");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.result()).isEqualTo(VectorStoreOperationResult.FAILURE);
            assertThat(event.errorCategory()).isEqualTo(VectorErrorCategory.PROVIDER_UNAVAILABLE);
        });
    }

    @Test
    void shouldPreserveImplicitTypedCapabilitiesAndIgnoreExporterFailure() {
        VectorStoreHandle observed = handle(service()).observed(event -> {
            throw new IllegalStateException("exporter down");
        });

        assertThat(observed.capability(VectorIndexStatsOperations.class)).isPresent();
        assertThat(observed.service().searchByText("logical-index", "query", 1, null)).isEmpty();
    }

    @Test
    void shouldDistinguishBasicRerankTunedMmrAndAclHybridPathsWithoutPayloads() {
        List<VectorStoreObservationEvent> events = new ArrayList<>();
        VectorStoreDefinition definition = definition();
        VectorAdvancedSearchOperations advanced = request -> new VectorSearchExecution(
                List.of(), new VectorSearchExecutionReceipt(
                        definition.id(), VectorProvider.MILVUS, "1.0", "1.0", "0123456789abcdef", List.of()));
        VectorAclAwareHybridSearchOperations hybrid = new VectorAclAwareHybridSearchOperations() {
            @Override
            public List<cn.richie696.component.vector.model.VectorSearchResult> hybridSearch(
                    String indexName, String text, String keywordQuery, int limit, HybridSearchOptions options) {
                throw new UnsupportedOperationException("explicit ACL filter required");
            }

            @Override
            public List<cn.richie696.component.vector.model.VectorSearchResult> hybridSearch(
                    String indexName, String text, String keywordQuery, int limit,
                    HybridSearchOptions options, VectorFilter filter) {
                return List.of();
            }
        };
        VectorStoreHandle observed = VectorStoreHandle.builder(definition, VectorProvider.MILVUS, service())
                .storeCapabilities(new VectorStoreCapabilities(List.of(
                        VectorCapabilityDescriptor.supported(VectorCapability.QUERY_TUNING),
                        VectorCapabilityDescriptor.supported(VectorCapability.ACL_SAFE_HYBRID))))
                .capability(VectorAdvancedSearchOperations.class, advanced)
                .capability(VectorAclAwareHybridSearchOperations.class, hybrid)
                .build()
                .observed(events::add);

        observed.service().searchByText(
                "logical-index", "private basic query", 2, SearchOptions.builder().rerank(false).build());
        observed.service().searchByText(
                "logical-index", "private rerank query", 2, SearchOptions.builder().rerank(true).build());
        observed.requireCapability(VectorAdvancedSearchOperations.class).search(new VectorQueryRequest(
                "private mmr query", 2, null, null, Set.of(), 4, null, null,
                new VectorDiversificationOptions(false, true, 0.5D), null));
        observed.requireCapability(VectorAclAwareHybridSearchOperations.class).hybridSearch(
                "logical-index", "private hybrid query", "secret keyword", 2, null,
                VectorFilter.eq("tenantId", "secret-principal"));

        assertThat(events).extracting(VectorStoreObservationEvent::operation).containsExactly(
                VectorStoreOperation.SEARCH_TEXT,
                VectorStoreOperation.SEARCH_RERANK,
                VectorStoreOperation.SEARCH_MMR,
                VectorStoreOperation.SEARCH_HYBRID);
        assertThat(events.toString())
                .doesNotContain("private basic query", "private rerank query", "private mmr query",
                        "private hybrid query", "secret keyword", "secret-principal");
    }

    private static VectorStoreHandle handle(VectorService service) {
        VectorStoreDefinition definition = definition();
        return VectorStoreHandle.builder(definition, VectorProvider.MILVUS, service)
                .storeCapabilities(VectorStoreCapabilities.of(VectorCapability.NATIVE_FILTER))
                .build();
    }

    private static VectorStoreDefinition definition() {
        return new VectorStoreDefinition(
                VectorStoreId.of("documents"),
                VectorConnectionId.of("primary"),
                "aiEmbeddingModel",
                "logical-index",
                true,
                Set.of());
    }

    private static VectorService service() {
        return (VectorService) Proxy.newProxyInstance(
                VectorService.class.getClassLoader(),
                new Class<?>[]{VectorService.class, VectorIndexStatsOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "searchByText", "searchByImage", "listIndexes" -> List.of();
                    case "healthCheck" -> true;
                    case "countDocuments" -> 0L;
                    case "deleteById" -> throw new VectorProviderUnavailableException(
                            "provider unavailable", new IllegalStateException("secret host"));
                    case "upsertAll", "deleteAll" -> reactor.core.publisher.Flux.empty();
                    case "toString" -> "ObservedDelegate";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
