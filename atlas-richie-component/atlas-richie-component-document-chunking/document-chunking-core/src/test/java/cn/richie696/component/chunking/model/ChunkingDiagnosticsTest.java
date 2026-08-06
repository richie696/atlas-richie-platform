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

@DisplayName("ChunkingDiagnostics record contract")
class ChunkingDiagnosticsTest {

    @Test
    @DisplayName("accessors return the values supplied to the canonical constructor")
    void diagnostics_accessors_returnConstructorValues() {
        ChunkingDiagnostics diagnostics = new ChunkingDiagnostics(true, 256);

        assertThat(diagnostics.hardTruncated()).isTrue();
        assertThat(diagnostics.inputCharacters()).isEqualTo(256);
    }

    @Test
    @DisplayName("record equality matches when both fields are equal")
    void diagnostics_recordEquality_dependsOnAllFields() {
        ChunkingDiagnostics a = new ChunkingDiagnostics(true, 100);
        ChunkingDiagnostics b = new ChunkingDiagnostics(true, 100);
        ChunkingDiagnostics truncatedFlagDiff = new ChunkingDiagnostics(false, 100);
        ChunkingDiagnostics sizeDiff = new ChunkingDiagnostics(true, 101);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(truncatedFlagDiff);
        assertThat(a).isNotEqualTo(sizeDiff);
    }

    @Test
    @DisplayName("zero-value construction is permitted")
    void diagnostics_zeroValues_areAllowed() {
        ChunkingDiagnostics diagnostics = new ChunkingDiagnostics(false, 0);

        assertThat(diagnostics.hardTruncated()).isFalse();
        assertThat(diagnostics.inputCharacters()).isZero();
    }
}