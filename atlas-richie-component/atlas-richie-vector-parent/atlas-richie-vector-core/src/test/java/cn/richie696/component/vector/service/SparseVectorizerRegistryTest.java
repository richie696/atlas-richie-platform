package cn.richie696.component.vector.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SparseVectorizerRegistryTest {

    @Test
    void resolvesNamedEncoderWithoutExposingContainer() {
        SparseVectorizer vectorizer = text -> new SparseVector(Map.of(1L, 1F));
        SparseVectorizerRegistry registry = new SparseVectorizerRegistry(Map.of("sparse", vectorizer));

        assertThat(registry.require("sparse")).isSameAs(vectorizer);
        assertThat(registry.contains("sparse")).isTrue();
        assertThatThrownBy(() -> registry.require("missing")).isInstanceOf(IllegalArgumentException.class);
    }
}
