/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.chunking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Chunk record contract")
class ChunkTest {

    @Test
    @DisplayName("accepts a minimal valid slice with ordinal=0 and adjacent offsets")
    void chunk_withMinimalValidArgs_shouldNotThrow() {
        Chunk chunk = new Chunk(0, "a", 0, 1);

        assertThat(chunk.ordinal()).isZero();
        assertThat(chunk.text()).isEqualTo("a");
        assertThat(chunk.charStart()).isZero();
        assertThat(chunk.charEnd()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects negative ordinal")
    void chunk_whenOrdinalIsNegative_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new Chunk(-1, "a", 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null text")
    void chunk_whenTextIsNull_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new Chunk(0, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects blank text (empty / whitespace)")
    void chunk_whenTextIsBlank_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new Chunk(0, "", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunk(0, "   ", 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects negative charStart")
    void chunk_whenCharStartIsNegative_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new Chunk(0, "a", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects charEnd < charStart")
    void chunk_whenCharEndIsBeforeCharStart_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new Chunk(0, "ab", 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("record equality and accessors")
    void chunk_recordEqualityAndAccessors() {
        Chunk a = new Chunk(2, "hello", 10, 15);
        Chunk b = new Chunk(2, "hello", 10, 15);
        Chunk c = new Chunk(3, "hello", 10, 15);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.text()).isEqualTo("hello");
        assertThat(a.charStart()).isEqualTo(10);
        assertThat(a.charEnd()).isEqualTo(15);
    }
}