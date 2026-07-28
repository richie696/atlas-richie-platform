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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChunkingResult record contract")
class ChunkingResultTest {

    @Test
    @DisplayName("single-arg constructor defaults diagnostics to a fresh zero diagnostics")
    void chunkingResult_singleArgConstructor_appliesDefaultDiagnostics() {
        ChunkingResult result = new ChunkingResult(List.of());

        assertThat(result.chunks()).isEmpty();
        assertThat(result.diagnostics()).isNotNull();
        assertThat(result.diagnostics().hardTruncated()).isFalse();
        assertThat(result.diagnostics().inputCharacters()).isZero();
    }

    @Test
    @DisplayName("two-arg constructor honors the supplied diagnostics")
    void chunkingResult_twoArgConstructor_keepsDiagnostics() {
        ChunkingDiagnostics diag = new ChunkingDiagnostics(true, 42);
        ChunkingResult result = new ChunkingResult(List.of(), diag);

        assertThat(result.diagnostics()).isSameAs(diag);
        assertThat(result.diagnostics().hardTruncated()).isTrue();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo(42);
    }

    @Test
    @DisplayName("compact constructor copies the input list so external mutation cannot leak in")
    void chunkingResult_compactConstructor_isImmutableToExternalMutation() {
        List<Chunk> source = new ArrayList<>();
        source.add(new Chunk(0, "alpha", 0, 5));

        ChunkingResult result = new ChunkingResult(source);
        source.clear();
        source.add(new Chunk(0, "modified", 0, 8));

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().text()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("null diagnostics is replaced with a zero-default sentinel")
    void chunkingResult_nullDiagnostics_fallsBackToZeroDefault() {
        ChunkingResult result = new ChunkingResult(List.of(), null);

        assertThat(result.diagnostics()).isNotNull();
        assertThat(result.diagnostics().hardTruncated()).isFalse();
        assertThat(result.diagnostics().inputCharacters()).isZero();
    }

    @Test
    @DisplayName("null chunks causes List.copyOf to throw NullPointerException (documented current behavior)")
    void chunkingResult_nullChunks_throwsNullPointer() {
        assertThatThrownBy(() -> new ChunkingResult(null, new ChunkingDiagnostics(false, 7)))
                .isInstanceOf(NullPointerException.class);
    }
}