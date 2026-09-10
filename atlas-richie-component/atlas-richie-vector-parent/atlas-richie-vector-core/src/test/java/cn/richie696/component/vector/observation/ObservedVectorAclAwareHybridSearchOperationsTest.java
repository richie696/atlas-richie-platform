package cn.richie696.component.vector.observation;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedVectorAclAwareHybridSearchOperationsTest {
    @Test
    void emitsCoreRrfExecutionWithoutQueryOrAclPayload() {
        AtomicReference<VectorStoreObservationEvent> event = new AtomicReference<>();
        VectorAclAwareHybridSearchOperations delegate = new CoreRrfDelegate();
        ObservedVectorAclAwareHybridSearchOperations observed = new ObservedVectorAclAwareHybridSearchOperations(
                delegate, VectorStoreId.of("store"), VectorProvider.REDIS, java.util.Set.of("ACL_SAFE_HYBRID"), event::set);

        observed.hybridSearch("documents", "private-query", "private-keyword", 1, null,
                VectorFilter.eq("tenantId", "tenant-a"));

        assertThat(event.get().execution()).isEqualTo("core-rrf");
        assertThat(event.get().traceAttributes().values())
                .noneMatch(value -> value.contains("private-query") || value.contains("tenant-a"));
    }

    private static final class CoreRrfDelegate implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
        @Override public String hybridExecutionMode() { return "core-rrf"; }
        @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keyword, int limit, HybridSearchOptions options) { throw new UnsupportedOperationException(); }
        @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keyword, int limit, HybridSearchOptions options, VectorFilter filter) { return List.of(VectorSearchResult.of("id", "content", 1D)); }
    }
}
