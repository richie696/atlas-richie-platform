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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingRule.Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChunkingServiceTest {

    private final DefaultChunkingService service = new DefaultChunkingService();

    /* ---------------------------------------------------------------------- */
    /* Existing tests (preserved verbatim for backward compatibility)         */
    /* ---------------------------------------------------------------------- */

    @Test
    void producesBoundedAndOrderedChunks() {
        var result = service.chunk("第一段内容。第二段内容。第三段内容。",
                new ChunkingRule("r", "1", ChunkingRule.Strategy.SENTENCE, 8, 2, null));
        assertFalse(result.chunks().isEmpty());
        assertTrue(result.chunks().stream().allMatch(c -> c.text().length() <= 8));
        assertEquals(result.chunks().stream().map(c -> c.text()).collect(Collectors.joining("")).replace(" ", "").contains("第一段"), true);
    }

    @Test
    void preservesInputOffsets() {
        String text = "alpha beta gamma";
        var r = service.chunk(text, ChunkingRule.recursiveDefaults(8, 0));
        r.chunks().forEach(c -> assertEquals(c.text(), text.substring(c.charStart(), c.charEnd())));
    }

    @Test
    void streamingFlushesTail() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(10, 2));
        assertTrue(stream.accept("abcdefgh").isEmpty());
        var all = new ArrayList<>(stream.accept("ijklmnop"));
        all.addAll(stream.finish());
        assertFalse(all.isEmpty());
        assertEquals(0, all.getFirst().ordinal());
    }

    /* ---------------------------------------------------------------------- */
    /* 8-strategy matrix                                                      */
    /* ---------------------------------------------------------------------- */

    private static void assertChunkInvariants(List<Chunk> chunks, String original) {
        assertThat(chunks).isNotEmpty();
        int prevOrdinal = -1;
        for (Chunk c : chunks) {
            assertThat(c.ordinal())
                    .as("ordinal must monotonically increase by 1")
                    .isEqualTo(prevOrdinal + 1);
            assertThat(c.text()).isNotEmpty();
            assertThat(c.charStart())
                    .as("charStart must be within the source")
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(c.charEnd());
            assertThat(original.substring(c.charStart(), c.charEnd()))
                    .as("text() must equal the source slice [charStart, charEnd)")
                    .isEqualTo(c.text());
            prevOrdinal = c.ordinal();
        }
    }

    @Test
    @DisplayName("FIXED strategy slices at maxCharacters without consulting separators")
    void chunk_withFixedStrategy_respectsMaxCharacters() {
        String text = "This is a long sentence with no special characters at all, just plain ASCII.";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.FIXED, 12, 0, null));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.chunks()).allSatisfy(c ->
                assertThat(c.text().length()).isLessThanOrEqualTo(12));
        assertThat(result.diagnostics().hardTruncated()).isFalse();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("TOKEN strategy behaves like FIXED (no separators)")
    void chunk_withTokenStrategy_skipsBoundaryCheck() {
        String text = "abcdefghijklmnopqrstuvwxyz0123456789";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.TOKEN, 8, 0, null));

        assertChunkInvariants(result.chunks(), text);
    }

    @Test
    @DisplayName("PARAGRAPH strategy respects double newlines as primary boundary")
    void chunk_withParagraphStrategy_splitsOnBlankLines() {
        String text = "First paragraph content.\n\nSecond paragraph content.\n\nThird paragraph content.";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.PARAGRAPH, 24, 0, null));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.chunks()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("SENTENCE strategy splits on Chinese and English sentence terminators")
    void chunk_withSentenceStrategy_preservesSentenceBoundaries() {
        String text = "第一段内容。第二段内容。第三段内容。Fourth sentence here. Fifth sentence there.";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.SENTENCE, 10, 0, null));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.chunks()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("MARKDOWN strategy prefers header breaks over paragraph breaks")
    void chunk_withMarkdownStrategy_prefersHeaderBoundaries() {
        String text = "# Intro\nintro body\n\n## Section A\nsection a body\n\n## Section B\nsection b body";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.MARKDOWN, 30, 0, null));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.chunks()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("HTML strategy splits on common tag boundaries")
    void chunk_withHtmlStrategy_splitsOnTags() {
        String text = "<p>First paragraph here.</p>\n<p>Second paragraph here.</p>\n<li>First item</li>\n<li>Second item</li>";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.HTML, 35, 0, null));

        assertChunkInvariants(result.chunks(), text);
    }

    @Test
    @DisplayName("PAGE strategy splits on form feed and blank lines")
    void chunk_withPageStrategy_splitsOnFormFeed() {
        String text = "Page 1 content.\fPage 2 content.\fPage 3 content.";
        ChunkingResult result = service.chunk(text, new ChunkingRule("r", "1", Strategy.PAGE, 14, 0, null));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.chunks()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("RECURSIVE strategy applies the user-supplied separator list")
    void chunk_withRecursiveStrategy_usesRuleSeparators() {
        String text = "alpha::beta::gamma::delta::epsilon::zeta";
        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.RECURSIVE, 12, 0, List.of("::")));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.chunks()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("SEMANTIC strategy throws IllegalArgumentException directing callers to provide an advisor")
    void chunk_withSemanticStrategy_throwsIllegalArgument() {
        ChunkingRule rule = new ChunkingRule("r", "1", Strategy.SEMANTIC, 12, 0, null);

        assertThatThrownBy(() -> service.chunk("anything", rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEMANTIC");
    }

    /* ---------------------------------------------------------------------- */
    /* Edge cases                                                             */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("null content returns an empty result with zero inputCharacters")
    void chunk_whenContentIsNull_returnsEmptyResultWithZeroLength() {
        ChunkingResult result = service.chunk(null, ChunkingRule.recursiveDefaults(10, 0));

        assertThat(result.chunks()).isEmpty();
        assertThat(result.diagnostics().inputCharacters()).isZero();
        assertThat(result.diagnostics().hardTruncated()).isFalse();
    }

    @Test
    @DisplayName("blank content (whitespace only) returns an empty result with the raw length preserved")
    void chunk_whenContentIsBlank_returnsEmptyResultWithRawLength() {
        ChunkingResult result = service.chunk("   \n\t  ", ChunkingRule.recursiveDefaults(10, 0));

        assertThat(result.chunks()).isEmpty();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo("   \n\t  ".length());
    }

    @Test
    @DisplayName("overlap=0 produces non-overlapping contiguous chunks")
    void chunk_withZeroOverlap_producesContiguousChunks() {
        String text = "abcdefghijklmnopqrstuvwxyz";
        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.FIXED, 6, 0, null));

        assertChunkInvariants(result.chunks(), text);
        for (int i = 1; i < result.chunks().size(); i++) {
            Chunk prev = result.chunks().get(i - 1);
            Chunk curr = result.chunks().get(i);
            assertThat(curr.charStart())
                    .as("chunks must be contiguous when overlap is zero")
                    .isEqualTo(prev.charEnd());
        }
    }

    @Test
    @DisplayName("overlap=1 keeps a 1-character overlap between consecutive chunks")
    void chunk_withUnitOverlap_keepsOneCharOverlap() {
        String text = "abcdefghijklmnopqrstuvwxyz";
        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.FIXED, 6, 1, null));

        assertChunkInvariants(result.chunks(), text);
        for (int i = 1; i < result.chunks().size(); i++) {
            Chunk prev = result.chunks().get(i - 1);
            Chunk curr = result.chunks().get(i);
            assertThat(curr.charStart())
                    .as("chunks must overlap by 1 character")
                    .isEqualTo(prev.charEnd() - 1);
        }
    }

    @Test
    @DisplayName("overlap = max-1 keeps the largest legal overlap")
    void chunk_withLargestOverlap_keepsMaxMinusOneChars() {
        String text = "abcdefghijklmnopqrstuvwxyz";
        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.FIXED, 6, 5, null));

        assertChunkInvariants(result.chunks(), text);
        for (int i = 1; i < result.chunks().size(); i++) {
            Chunk prev = result.chunks().get(i - 1);
            Chunk curr = result.chunks().get(i);
            assertThat(curr.charStart())
                    .as("chunks must overlap by max-1 characters")
                    .isEqualTo(prev.charEnd() - 5);
        }
    }

    @Test
    @DisplayName("content with no matching boundary marks diagnostics.hardTruncated=true")
    void chunk_withUnmatchableBoundary_marksHardTruncated() {
        // No sentence terminator within first maxCharacters => hard-truncated path
        String text = "A".repeat(50);
        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.SENTENCE, 10, 0, null));

        assertChunkInvariants(result.chunks(), text);
        assertThat(result.diagnostics().hardTruncated()).isTrue();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo(50);
    }

    @Test
    @DisplayName("leading and trailing whitespace are stripped from each chunk")
    void chunk_stripsLeadingAndTrailingWhitespaceFromChunks() {
        String text = "  hello world  ";
        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.FIXED, 30, 0, null));

        assertThat(result.chunks()).hasSize(1);
        Chunk only = result.chunks().getFirst();
        assertThat(only.text()).isEqualTo("hello world");
        assertThat(only.charStart()).isEqualTo(2);
        assertThat(only.charEnd()).isEqualTo(13);
    }

    @Test
    @DisplayName("chunk(content) uses recursiveDefaults(1600, 160) through the default rule")
    void chunk_withoutRule_usesRecursiveDefaultRule() {
        String text = "a".repeat(1_601);

        ChunkingResult result = service.chunk(text);

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks()).allSatisfy(chunk ->
                assertThat(chunk.text().length()).isLessThanOrEqualTo(1_600));
    }

    @Test
    @DisplayName("a document exceeding maxChunksPerDocument fails fast")
    void chunk_whenDocumentExceedsChunkLimit_throwsIllegalState() {
        DefaultChunkingService limited = new DefaultChunkingService(
                ChunkingRule.recursiveDefaults(2, 0), DefaultChunkingService.approximateTokenCounter(), 0, 1);

        assertThatThrownBy(() -> limited.chunk("abcdef"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("FIXED results expose non-truncated diagnostics and input length")
    void chunk_withFixedStrategy_exposesDiagnostics() {
        String text = "fixed diagnostics";

        ChunkingResult result = service.chunk(text,
                new ChunkingRule("r", "1", Strategy.FIXED, 100, 0, null));

        assertThat(result.diagnostics().hardTruncated()).isFalse();
        assertThat(result.diagnostics().inputCharacters()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("TOKEN strategy uses a caller-supplied token counter")
    void chunk_withTokenStrategy_usesCustomTokenCounter() {
        List<String> countedTexts = new ArrayList<>();
        DefaultChunkingService custom = new DefaultChunkingService(
                ChunkingRule.recursiveDefaults(3, 0),
                text -> {
                    countedTexts.add(text);
                    return text.length();
                }, 0, 100);
        ChunkingRule tokenRule = new ChunkingRule("token", "1", Strategy.TOKEN, 3, 0, null);

        ChunkingResult result = custom.chunk("abcdef", tokenRule);

        assertThat(countedTexts).isNotEmpty();
        assertThat(result.chunks()).allSatisfy(chunk ->
                assertThat(chunk.text().length()).isLessThanOrEqualTo(3));
    }
}

