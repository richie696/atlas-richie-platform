package cn.richie696.component.vector.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SparseVectorTest {

    @Test
    void acceptsFiniteNonNegativeSparseCoordinates() {
        SparseVector vector = new SparseVector(Map.of(0L, 0.5F, 42L, -0.25F));

        assertThat(vector.coordinates()).containsEntry(0L, 0.5F).containsEntry(42L, -0.25F);
    }

    @Test
    void rejectsEmptyOrInvalidSparseCoordinates() {
        assertThatThrownBy(() -> new SparseVector(Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SparseVector(Map.of(-1L, 1.0F))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SparseVector(Map.of(1L, Float.NaN))).isInstanceOf(IllegalArgumentException.class);
    }
}
