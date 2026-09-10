package cn.richie696.component.vector.service.support;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AclSafeHybridSearchFallbackTest {

    @Test
    void fusesOnlyCandidatesAlreadyReturnedByAclFilteredBranches() {
        VectorSearchResult denseOnly = VectorSearchResult.of("dense-only", "dense", 0.9D);
        VectorSearchResult sharedDense = VectorSearchResult.of("shared", "dense-shared", 0.8D);
        VectorSearchResult sharedSparse = VectorSearchResult.of("shared", "sparse-shared", 0.7D);
        VectorSearchResult sparseOnly = VectorSearchResult.of("sparse-only", "sparse", 0.6D);

        List<VectorSearchResult> results = AclSafeHybridSearchFallback.fuse(
                List.of(denseOnly, sharedDense), List.of(sharedSparse, sparseOnly),
                HybridSearchOptions.builder().vectorWeight(0.7D).keywordWeight(0.3D).build(), 3);

        assertThat(results).extracting(VectorSearchResult::getId)
                .containsExactly("shared", "dense-only", "sparse-only");
        assertThat(results).extracting(VectorSearchResult::getContent).contains("dense-shared");
        assertThat(results).doesNotContain(denseOnly, sharedDense, sharedSparse, sparseOnly);
    }

    @Test
    void fallsBackOnlyForProviderClassifiedCapabilityMismatch() {
        List<VectorSearchResult> results = AclSafeHybridSearchFallback.nativeFirst(
                () -> { throw new UnsupportedOperationException("native hybrid is unavailable"); },
                () -> List.of(VectorSearchResult.of("dense", "dense", 1.0D)),
                () -> List.of(VectorSearchResult.of("sparse", "sparse", 1.0D)),
                HybridSearchOptions.builder().build(), 2,
                UnsupportedOperationException.class::isInstance);

        assertThat(results).extracting(VectorSearchResult::getId).containsExactly("dense", "sparse");
        assertThatThrownBy(() -> AclSafeHybridSearchFallback.nativeFirst(
                () -> { throw new IllegalStateException("authentication failed"); },
                List::<VectorSearchResult>of, List::<VectorSearchResult>of,
                HybridSearchOptions.builder().build(), 1,
                UnsupportedOperationException.class::isInstance))
                .isInstanceOf(IllegalStateException.class).hasMessage("authentication failed");
    }

    @Test
    void doesNotInvokeEitherFallbackBranchWhenNativeFailureIsNotClassified() {
        java.util.concurrent.atomic.AtomicBoolean recalled = new java.util.concurrent.atomic.AtomicBoolean();
        assertThatThrownBy(() -> AclSafeHybridSearchFallback.nativeFirst(
                () -> { throw new SecurityException("permission denied"); },
                () -> { recalled.set(true); return List.of(); }, () -> { recalled.set(true); return List.of(); },
                HybridSearchOptions.builder().build(), 1, UnsupportedOperationException.class::isInstance))
                .isInstanceOf(SecurityException.class);
        assertThat(recalled).isFalse();
    }
}
