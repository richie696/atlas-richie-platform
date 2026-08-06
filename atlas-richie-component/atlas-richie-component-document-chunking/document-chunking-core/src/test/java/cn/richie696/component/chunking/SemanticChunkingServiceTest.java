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
package cn.richie696.component.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingRule.Strategy;
import cn.richie696.component.chunking.spi.SemanticBoundaryAdvisor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticChunkingService — boundary advisor + fallback orchestrator")
class SemanticChunkingServiceTest {

    @Mock
    private ChunkingService fallback;

    @Mock
    private SemanticBoundaryAdvisor advisor;

    private ChunkingRule rule;

    @BeforeEach
    void setUp() {
        rule = new ChunkingRule("r", "1", Strategy.SEMANTIC, 12, 0, null);
    }

    /* ---------------------------------------------------------------------- */
    /* Construction                                                            */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("constructor rejects null fallback")
    void constructor_nullFallback_throwsNullPointer() {
        assertThatThrownBy(() -> new SemanticChunkingService(null, advisor))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null advisor")
    void constructor_nullAdvisor_throwsNullPointer() {
        assertThatThrownBy(() -> new SemanticChunkingService(fallback, null))
                .isInstanceOf(NullPointerException.class);
    }

    /* ---------------------------------------------------------------------- */
    /* Empty boundaries — fall back to RECURSIVE                               */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("empty boundaries delegate to the fallback with RECURSIVE strategy")
    void chunk_whenAdvisorReturnsEmpty_delegatesToFallbackWithRecursiveStrategy() {
        when(advisor.boundaries("anything")).thenReturn(List.of());
        when(fallback.chunk(eq("anything"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "anything", 0, 8))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk("anything", rule);

        assertThat(result.chunks()).hasSize(1);
        verify(fallback, times(1)).chunk(eq("anything"), any(ChunkingRule.class));
    }

    /* ---------------------------------------------------------------------- */
    /* Multiple boundaries — slice + offset-shift                              */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("multiple boundaries split the content into segments that are each chunked by the fallback")
    void chunk_withMultipleBoundaries_invokesFallbackOncePerSegment() {
        String content = "abcdefghijklmnopqrstuvwxyz";
        when(advisor.boundaries(content)).thenReturn(List.of(5, 12));

        when(fallback.chunk(eq("abcde"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "abcde", 0, 5))));
        when(fallback.chunk(eq("fghijkl"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "fghijkl", 0, 7))));
        when(fallback.chunk(eq("mnopqrstuvwxyz"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "mnopqrstuvwxyz", 0, 14))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        verify(fallback).chunk(eq("abcde"), any(ChunkingRule.class));
        verify(fallback).chunk(eq("fghijkl"), any(ChunkingRule.class));
        verify(fallback).chunk(eq("mnopqrstuvwxyz"), any(ChunkingRule.class));
        verify(fallback, times(3)).chunk(any(String.class), any(ChunkingRule.class));

        assertThat(result.chunks()).hasSize(3);
        assertThat(result.chunks().get(0).text()).isEqualTo("abcde");
        assertThat(result.chunks().get(0).charStart()).isZero();
        assertThat(result.chunks().get(0).charEnd()).isEqualTo(5);

        assertThat(result.chunks().get(1).text()).isEqualTo("fghijkl");
        assertThat(result.chunks().get(1).charStart()).isEqualTo(5);
        assertThat(result.chunks().get(1).charEnd()).isEqualTo(12);

        assertThat(result.chunks().get(2).text()).isEqualTo("mnopqrstuvwxyz");
        assertThat(result.chunks().get(2).charStart()).isEqualTo(12);
        assertThat(result.chunks().get(2).charEnd()).isEqualTo(26);

        assertThat(result.diagnostics().hardTruncated()).isFalse();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo(26);
    }

    /* ---------------------------------------------------------------------- */
    /* Cross-segment ordinal continuity                                        */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("ordinals continue across segment boundaries in the global result list")
    void chunk_withMultipleSegments_ordinalsAreGloballyMonotonic() {
        String content = "abcdefghijklmnopqrst";
        when(advisor.boundaries(content)).thenReturn(List.of(6, 12));

        when(fallback.chunk(eq("abcdef"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(
                        new Chunk(0, "ab", 0, 2),
                        new Chunk(1, "cd", 2, 4),
                        new Chunk(2, "ef", 4, 6))));
        when(fallback.chunk(eq("ghijkl"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "ghijkl", 0, 6))));
        when(fallback.chunk(eq("mnopqrst"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(
                        new Chunk(0, "mn", 0, 2),
                        new Chunk(1, "opqrst", 2, 8))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        assertThat(result.chunks()).extracting(Chunk::ordinal)
                .containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(result.chunks().get(0).text()).isEqualTo("ab");
        assertThat(result.chunks().get(1).text()).isEqualTo("cd");
        assertThat(result.chunks().get(2).text()).isEqualTo("ef");
        assertThat(result.chunks().get(3).text()).isEqualTo("ghijkl");
        assertThat(result.chunks().get(4).text()).isEqualTo("mn");
        assertThat(result.chunks().get(5).text()).isEqualTo("opqrst");
    }

    /* ---------------------------------------------------------------------- */
    /* Boundary at content.length() — the trailing segment is a no-op         */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("a boundary at content.length() is allowed and yields no empty trailing segment")
    void chunk_whenBoundaryEqualsContentLength_doesNotProduceEmptyTrailingChunk() {
        String content = "abcdef";
        when(advisor.boundaries(content)).thenReturn(List.of(6));

        when(fallback.chunk(eq("abcdef"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "abcdef", 0, 6))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        verify(fallback, times(1)).chunk(eq("abcdef"), any(ChunkingRule.class));
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().text()).isEqualTo("abcdef");
    }

    /* ---------------------------------------------------------------------- */
    /* Boundary beyond content.length() — filtered out → fallback to RECURSIVE */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("a boundary beyond content.length() is filtered out and falls back to deterministic chunking")
    void chunk_whenBoundaryIsBeyondContentLength_fallsBackToDeterministicChunking() {
        String content = "abcdef";
        when(advisor.boundaries(content)).thenReturn(List.of(100));
        when(fallback.chunk(eq(content), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "abcdef", 0, 6))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        verify(fallback, times(1)).chunk(eq(content), any(ChunkingRule.class));
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().text()).isEqualTo("abcdef");
    }

    /* ---------------------------------------------------------------------- */
    /* Advisor returns null — filtered out → fallback to RECURSIVE             */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("advisor returning null boundaries falls back to the deterministic chunking service")
    void chunk_whenAdvisorReturnsNull_usesFallbackChunkingService() {
        when(advisor.boundaries("anything")).thenReturn(null);
        when(fallback.chunk(eq("anything"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "anything", 0, 8))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk("anything", rule);

        verify(fallback, times(1)).chunk(eq("anything"), any(ChunkingRule.class));
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().text()).isEqualTo("anything");
    }

    /* ---------------------------------------------------------------------- */
    /* Skipped segments — end <= start returns early                           */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("a boundary equal to the current start produces a skipped empty segment")
    void chunk_whenBoundaryEqualsCurrentStart_skipsEmptySegment() {
        String content = "abcdef";
        // First boundary equals 0 — start is also 0 → end <= start → skip.
        // Then start stays at 0 for the trailing segment.
        when(advisor.boundaries(content)).thenReturn(List.of(0));

        when(fallback.chunk(eq("abcdef"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "abcdef", 0, 6))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        verify(fallback, never()).chunk(eq(""), any(ChunkingRule.class));

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().text()).isEqualTo("abcdef");
    }

    @Test
    @DisplayName("null content returns an empty result without consulting the advisor")
    void chunk_withNullContent_returnsEmptyResult() {
        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(null, rule);

        assertThat(result.chunks()).isEmpty();
        assertThat(result.diagnostics().inputCharacters()).isZero();
        verifyNoInteractions(advisor, fallback);
    }

    @Test
    @DisplayName("blank content returns an empty result without consulting the advisor")
    void chunk_withBlankContent_returnsEmptyResult() {
        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk("  \n\t", rule);

        assertThat(result.chunks()).isEmpty();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo(4);
        verifyNoInteractions(advisor, fallback);
    }

    @Test
    @DisplayName("a null rule is rejected before advisor invocation")
    void chunk_withNullRule_throwsNullPointer() {
        assertThatThrownBy(() -> new SemanticChunkingService(fallback, advisor).chunk("content", null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(advisor, fallback);
    }

    @Test
    @DisplayName("a deterministic rule is rejected by the semantic service")
    void chunk_withFixedRule_throwsIllegalArgument() {
        ChunkingRule fixed = new ChunkingRule("fixed", "1", Strategy.FIXED, 12, 0, null);

        assertThatThrownBy(() -> new SemanticChunkingService(fallback, advisor).chunk("content", fixed))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(advisor, fallback);
    }

    @Test
    @DisplayName("duplicate advisor boundaries are emitted only once")
    void chunk_withDuplicateBoundaries_deduplicatesBoundaries() {
        String content = "abcdefghij";
        when(advisor.boundaries(content)).thenReturn(List.of(4, 4));
        when(fallback.chunk(eq("abcd"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "abcd", 0, 4))));
        when(fallback.chunk(eq("efghij"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "efghij", 0, 6))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        verify(fallback, times(2)).chunk(any(String.class), any(ChunkingRule.class));
        assertThat(result.chunks()).extracting(Chunk::text).containsExactly("abcd", "efghij");
    }

    @Test
    @DisplayName("out-of-order advisor boundaries are sorted before segmentation")
    void chunk_withOutOfOrderBoundaries_sortsBoundaries() {
        String content = "abcdefghij";
        when(advisor.boundaries(content)).thenReturn(List.of(7, 2));
        when(fallback.chunk(eq("ab"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "ab", 0, 2))));
        when(fallback.chunk(eq("cdefg"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "cdefg", 0, 5))));
        when(fallback.chunk(eq("hij"), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "hij", 0, 3))));

        ChunkingResult result = new SemanticChunkingService(fallback, advisor).chunk(content, rule);

        assertThat(result.chunks()).extracting(Chunk::text).containsExactly("ab", "cdefg", "hij");
        verify(fallback).chunk(eq("ab"), any(ChunkingRule.class));
        verify(fallback).chunk(eq("cdefg"), any(ChunkingRule.class));
        verify(fallback).chunk(eq("hij"), any(ChunkingRule.class));
    }
}

