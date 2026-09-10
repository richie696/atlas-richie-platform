package cn.richie696.component.vector.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridStoreOptionsTest {

    @Test
    void parsesDefaultDenseOnlyDeclaration() {
        HybridStoreOptions options = HybridStoreOptions.fromAdditionalFields(Map.of());

        assertThat(options.enabled()).isFalse();
        assertThat(options.denseField()).isEqualTo("vector");
        assertThat(options.sparseField()).isEqualTo("sparse_vector");
        assertThat(options.candidateLimit()).isEqualTo(50);
        assertThat(options.fallbackMode()).isEqualTo(HybridStoreOptions.FallbackMode.REJECT);
    }

    @Test
    void parsesHybridDeclarationAndRejectsBadFallback() {
        HybridStoreOptions options = HybridStoreOptions.fromAdditionalFields(Map.of(
                "hybrid-enabled", true,
                "dense-field", "dense",
                "sparse-field", "sparse",
                "sparse-vectorizer", "bm25Encoder",
                "hybrid-candidate-limit", "75",
                "hybrid-fallback-mode", "core_rrf"));

        assertThat(options.enabled()).isTrue();
        assertThat(options.vectorizerBeanName()).isEqualTo("bm25Encoder");
        assertThat(options.fallbackMode()).isEqualTo(HybridStoreOptions.FallbackMode.CORE_RRF);
        assertThatThrownBy(() -> HybridStoreOptions.fromAdditionalFields(Map.of("hybrid-fallback-mode", "anything")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validatesEnabledHybridRequirements() {
        assertThat(new HybridStoreOptions(true, "vector", "sparse", "bm25", 20,
                HybridStoreOptions.FallbackMode.CORE_RRF).candidateLimit()).isEqualTo(20);
        assertThat(new HybridStoreOptions(true, "vector", "sparse", null, 20, null)
                .vectorizerBeanName()).isNull();
        assertThatThrownBy(() -> new HybridStoreOptions(true, "vector", "vector", "bm25", 20, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
