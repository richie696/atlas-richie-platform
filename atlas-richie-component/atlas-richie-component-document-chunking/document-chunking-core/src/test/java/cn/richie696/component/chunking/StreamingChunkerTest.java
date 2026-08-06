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

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingRule.Strategy;
import cn.richie696.component.chunking.strategy.StreamingChunkingStrategy;
import cn.richie696.component.chunking.strategy.StreamingStrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StreamingChunker — incremental session with overlap-aware drain")
class StreamingChunkerTest {

    /**
     * StreamingChunker 构造时检查 {@code ChunkingService instanceof StreamingStrategyResolver}，
     * 所以 mock 必须同时实现两个接口——通过 {@code extraInterfaces} 添加额外接口。
     */
    @Mock(extraInterfaces = StreamingStrategyResolver.class)
    private ChunkingService service;

    @SuppressWarnings("unused")
    private StreamingStrategyResolver streamingResolver() {
        return (StreamingStrategyResolver) service;
    }

    /**
     * StreamingChunker 构造器会调用 {@code resolver.streamingStrategy(rule)} 并把结果存入
     * {@code streamingStrategy} 字段，drain 时直接调用它找边界——mock 默认返回 null 会导致 NPE，
     * 所以这里统一 stub 成返回一个空的 {@link StreamingChunkingStrategy} 实现。
     */
    @BeforeEach
    void stubStreamingStrategy() {
        lenient().when(streamingResolver().streamingStrategy(any(ChunkingRule.class)))
                .thenReturn(mock(StreamingChunkingStrategy.class));
    }

    /* ---------------------------------------------------------------------- */
    /* Construction                                                            */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("constructor rejects null service")
    void constructor_nullService_throwsNullPointer() {
        assertThatThrownBy(() -> new StreamingChunker(null, ChunkingRule.recursiveDefaults(10, 0)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null rule")
    void constructor_nullRule_throwsNullPointer() {
        assertThatThrownBy(() -> new StreamingChunker(service, null))
                .isInstanceOf(NullPointerException.class);
    }

    /* ---------------------------------------------------------------------- */
    /* accept() short-circuits on null / blank                                 */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("accept(null) returns empty list and does not touch the service")
    void accept_null_returnsEmptyList() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(10, 0));

        assertThat(stream.accept(null)).isEmpty();
    }

    @Test
    @DisplayName("accept(empty) returns empty list and does not touch the service")
    void accept_empty_returnsEmptyList() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(10, 0));

        assertThat(stream.accept("")).isEmpty();
    }

    @Test
    @DisplayName("accept(whitespace-only) returns empty list and does not touch the service")
    void accept_blank_returnsEmptyList() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(10, 0));

        assertThat(stream.accept("   \n\t  ")).isEmpty();
    }

    /* ---------------------------------------------------------------------- */
    /* accept() accumulation                                                   */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("accumulated buffer below maxCharacters does not trigger a drain")
    void accept_belowThreshold_doesNotDrain() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(20, 0));

        assertThat(stream.accept("hello")).isEmpty();
        assertThat(stream.accept("world")).isEmpty();

        verify(service, never()).chunk(anyString(), any(ChunkingRule.class));
    }

    @Test
    @DisplayName("accumulated buffer reaching maxCharacters triggers a drain")
    void accept_crossesThreshold_drainsOnce() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(10, 0);
        StreamingChunker stream = new StreamingChunker(service, rule);

        when(service.chunk(anyString(), any(ChunkingRule.class)))
                .thenReturn(new ChunkingResult(List.of(new Chunk(0, "abcdefghij", 0, 10))));

        List<Chunk> firstDrain = stream.accept("abcdefghij");

        assertThat(firstDrain).hasSize(1);
        assertThat(firstDrain.getFirst().ordinal()).isZero();
        assertThat(firstDrain.getFirst().text()).isEqualTo("abcdefghij");
        verify(service, times(1)).chunk(anyString(), any(ChunkingRule.class));
    }

    /* ---------------------------------------------------------------------- */
    /* finish() idempotency                                                    */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("finish() is idempotent — calling it twice does not re-emit")
    void finish_isIdempotent() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(10, 0);
        StreamingChunker stream = new StreamingChunker(service, rule);

        when(service.chunk(anyString(), any(ChunkingRule.class)))
                .thenAnswer(inv -> new ChunkingResult(List.of(new Chunk(0, inv.getArgument(0), 0,
                        ((String) inv.getArgument(0)).length()))));

        stream.accept("hello");
        List<Chunk> firstFinish = stream.finish();
        List<Chunk> secondFinish = stream.finish();

        assertThat(firstFinish).isNotEmpty();
        assertThat(secondFinish).isEmpty();
        verify(service, times(1)).chunk(anyString(), any(ChunkingRule.class));
    }

    /* ---------------------------------------------------------------------- */
    /* abort() drops the buffer                                                */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("abort() drops the pending buffer and rejects further accept() calls")
    void abort_clearsBuffer() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(10, 0);
        StreamingChunker stream = new StreamingChunker(service, rule);

        stream.accept("hi");
        stream.abort();

        assertThatThrownBy(() -> stream.accept("xyz"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已结束");
    }

    /* ---------------------------------------------------------------------- */
    /* Global ordinals across multiple drains                                  */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("ordinals across multiple drains form one monotonically increasing sequence")
    void accept_multipleDrains_ordinalsAreMonotonic() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(10, 0);
        StreamingChunker stream = new StreamingChunker(service, rule);

        when(service.chunk(anyString(), any(ChunkingRule.class)))
                .thenAnswer(inv -> {
                    String head = inv.getArgument(0);
                    return new ChunkingResult(List.of(new Chunk(0, head, 0, head.length())));
                });

        List<Chunk> all = new ArrayList<>();
        all.addAll(stream.accept("abcdefghij"));
        all.addAll(stream.accept("klmnopqrst"));
        all.addAll(stream.finish());

        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i).ordinal()).isEqualTo(all.get(i - 1).ordinal() + 1);
        }
        verify(service, atLeastOnce()).chunk(anyString(), any(ChunkingRule.class));
    }

    /* ---------------------------------------------------------------------- */
    /* Overlap-aware drain                                                      */
    /* ---------------------------------------------------------------------- */

    @Test
    @DisplayName("drain(continue) emits the full head and keeps the trailing overlap characters in pending for the next round")
    void drain_continue_keepsOverlapTail() throws Exception {
        ChunkingRule rule = new ChunkingRule("r", "1", Strategy.FIXED, 10, 3, null);
        StreamingChunker stream = new StreamingChunker(service, rule);

        when(service.chunk(anyString(), any(ChunkingRule.class)))
                .thenAnswer(inv -> {
                    String head = inv.getArgument(0);
                    return new ChunkingResult(List.of(new Chunk(0, head, 0, head.length())));
                });

        List<Chunk> firstDrain = stream.accept("abcdefghij");

        assertThat(firstDrain).hasSize(1);
        Chunk first = firstDrain.getFirst();
        assertThat(first.text())
                .as("emitted chunk must include the overlap tail; the consume step keeps only (end - overlap) chars")
                .isEqualTo("abcdefghij");
        assertThat(first.ordinal()).isZero();
        assertThat(first.charStart()).isZero();
        assertThat(first.charEnd()).isEqualTo(10);

        java.lang.reflect.Field pendingField = StreamingChunker.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        StringBuilder pending = (StringBuilder) pendingField.get(stream);
        assertThat(pending.toString())
                .as("trailing 3 chars (hij) are kept in pending for the next drain's overlap prefix")
                .isEqualTo("hij");
    }

    @Test
    @DisplayName("a follow-up accept emits a chunk whose prefix overlaps with the previous chunk's suffix")
    void drain_continue_nextAcceptOverlapsWithPreviousChunk() {
        ChunkingRule rule = new ChunkingRule("r", "1", Strategy.FIXED, 10, 3, null);
        StreamingChunker stream = new StreamingChunker(service, rule);

        when(service.chunk(anyString(), any(ChunkingRule.class)))
                .thenAnswer(inv -> {
                    String head = inv.getArgument(0);
                    return new ChunkingResult(List.of(new Chunk(0, head, 0, head.length())));
                });

        stream.accept("abcdefghij");
        List<Chunk> secondDrain = stream.accept("klmnopqrst");

        assertThat(secondDrain).isNotEmpty();
        String secondText = secondDrain.getFirst().text();
        assertThat(secondText)
                .as("second chunk must start with the overlap tail carried over from pending plus the newline separator")
                .startsWith("hij\n");
    }

    @Test
    @DisplayName("finish() rejects accept() after the session is closed")
    void accept_afterFinish_throwsIllegalState() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(10, 0));

        stream.finish();

        assertThatThrownBy(() -> stream.accept("late content"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    @DisplayName("abort() rejects accept() after the session is cancelled")
    void accept_afterAbort_throwsIllegalState() {
        StreamingChunker stream = new StreamingChunker(service, ChunkingRule.recursiveDefaults(10, 0));

        stream.abort();

        assertThatThrownBy(() -> stream.accept("late content"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    @DisplayName("accept rejects a pending buffer beyond its configured guard")
    void accept_whenPendingLimitIsExceeded_throwsIllegalState() throws ReflectiveOperationException {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(10, 0);
        StreamingChunker stream = new StreamingChunker(service, rule, 10);
        Field maximumPending = StreamingChunker.class.getDeclaredField("maxPendingCharacters");
        maximumPending.setAccessible(true);
        maximumPending.setInt(stream, 0);

        assertThatThrownBy(() -> stream.accept("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缓冲区超过上限");
    }

    @Test
    @DisplayName("offset overflow is rejected when mapping an emitted chunk")
    void finish_whenOffsetOverflows_throwsIllegalState() {
        ChunkingRule rule = new ChunkingRule("r", "1", Strategy.FIXED, 2, 0, null);
        StreamingChunker stream = new StreamingChunker(service, rule);
        when(service.chunk(anyString(), any(ChunkingRule.class)))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0);
                    int end = text.length() == 2 ? 2 : Integer.MAX_VALUE;
                    return new ChunkingResult(List.of(new Chunk(0, text, 0, end)));
                });

        stream.accept("ab");
        stream.accept("c");

        assertThatThrownBy(stream::finish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("偏移");
    }
}
