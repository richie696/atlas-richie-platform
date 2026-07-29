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
package cn.richie696.component.chunking.parser;

import cn.richie696.component.chunking.ChunkingService;
import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.parser.exception.DocumentParseException;
import cn.richie696.component.parser.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link ParserChunkingAdapter} 的核心门面。用 Mockito 替身 {@link ChunkingService}，
 * 验证三个入口契约：
 * <ul>
 *   <li>{@code chunk(ReadResult, ChunkingRule)} 批量入口</li>
 *   <li>{@code adapt(Publisher, ChunkingRule)} 仅发 {@link ChunkedSection}</li>
 *   <li>{@code adaptEvents(Publisher, ChunkingRule)} 完整事件流（含完成 / 失败）</li>
 * </ul>
 * <p>
 * 对于 {@link Flow.Publisher} 相关测试，使用 {@link RecordingTestPublisher} 代替
 * {@link java.util.concurrent.SubmissionPublisher}，避免异步线程调度带来的不稳定 —— 用
 * 同步、可重放、需求驱动的虚拟发布器，可精确验证上游 request/cancel/onNext/onError/onComplete 透传。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ParserChunkingAdapter 门面契约")
class ParserChunkingAdapterTest {

    @Mock
    private ChunkingService chunkingService;

    private ParserChunkingAdapter adapter;
    private ChunkingRule rule;

    @BeforeEach
    void setUp() {
        adapter = new ParserChunkingAdapter(chunkingService);
        rule = ChunkingRule.recursiveDefaults(100, 10);
    }

    private void stubFixedResult() {
        ChunkingResult result = new ChunkingResult(List.of(new Chunk(0, "x", 0, 1)));
        when(chunkingService.chunk(any(), eq(rule))).thenReturn(result);
    }

    private static ParsedSection section(String text, String path) {
        return new ParsedSection(text, path, Map.of("format", "text/plain"));
    }

    private static ReadResult readResult(ParsedSection... sections) {
        return new ReadResult("title", "author", List.of(sections), List.of(), Map.of("format", "text/plain"));
    }

    // ============================================================== chunk()

    @Nested
    @DisplayName("chunk(ReadResult, ChunkingRule) 批量入口")
    class ChunkBatch {

        @Test
        @DisplayName("多 section 按序产出 N 个 ChunkedSection，sectionIndex 从 0 递增")
        void chunk_whenMultipleSections_shouldEmitInOrder() {
            ParsedSection s1 = section("alpha", "/doc/0");
            ParsedSection s2 = section("beta", "/doc/1");
            ParsedSection s3 = section("gamma", "/doc/2");
            ReadResult result = readResult(s1, s2, s3);
            ChunkingResult cr1 = new ChunkingResult(List.of(new Chunk(0, "alpha", 0, 5)));
            ChunkingResult cr2 = new ChunkingResult(List.of(new Chunk(0, "beta", 0, 4)));
            ChunkingResult cr3 = new ChunkingResult(List.of(new Chunk(0, "gamma", 0, 5)));

            when(chunkingService.chunk("alpha", rule)).thenReturn(cr1);
            when(chunkingService.chunk("beta", rule)).thenReturn(cr2);
            when(chunkingService.chunk("gamma", rule)).thenReturn(cr3);

            List<ChunkedSection> out = adapter.chunk(result, rule);

            assertThat(out).hasSize(3);
            assertThat(out.get(0).sectionIndex()).isEqualTo(0);
            assertThat(out.get(1).sectionIndex()).isEqualTo(1);
            assertThat(out.get(2).sectionIndex()).isEqualTo(2);
            assertThat(out.get(0).source()).isSameAs(s1);
            assertThat(out.get(1).source()).isSameAs(s2);
            assertThat(out.get(2).source()).isSameAs(s3);
            assertThat(out.get(0).result()).isSameAs(cr1);
            assertThat(out.get(2).result()).isSameAs(cr3);
        }

        @Test
        @DisplayName("批量接口的 fileName 始终为 null（按实现）")
        void chunk_fileNameIsAlwaysNull() {
            stubFixedResult();
            ReadResult result = readResult(section("a", "/a"));

            List<ChunkedSection> out = adapter.chunk(result, rule);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).fileName()).isNull();
        }

        @Test
        @DisplayName("section.text() 被原样传给 ChunkingService")
        void chunk_delegatesEachSectionTextToService() {
            ParsedSection s1 = section("hello world", "/a");
            ParsedSection s2 = section("second section", "/b");
            stubFixedResult();

            adapter.chunk(readResult(s1, s2), rule);

            verify(chunkingService).chunk("hello world", rule);
            verify(chunkingService).chunk("second section", rule);
            verify(chunkingService, times(2)).chunk(any(), eq(rule));
        }

        @Test
        @DisplayName("sections 为空时返回空列表，且 ChunkingService 不被调用")
        void chunk_whenEmptySections_shouldReturnEmptyList() {
            ReadResult result = readResult();

            List<ChunkedSection> out = adapter.chunk(result, rule);

            assertThat(out).isEmpty();
            verify(chunkingService, times(0)).chunk(any(), any());
        }

        @Test
        @DisplayName("构造器 null chunkingService 抛 NPE")
        void chunk_constructorNpeOnNull() {
            assertThatNullPointerException().isThrownBy(() -> new ParserChunkingAdapter(null));
        }

        @Test
        @DisplayName("chunk() 收到 null ReadResult 抛 NPE")
        void chunk_npeOnNullResult() {
            assertThatNullPointerException().isThrownBy(() -> adapter.chunk(null, rule));
        }

        @Test
        @DisplayName("chunk() 收到 null ChunkingRule 抛 NPE")
        void chunk_npeOnNullRule() {
            assertThatNullPointerException().isThrownBy(() -> adapter.chunk(readResult(), null));
        }

        @Test
        @DisplayName("ChunkingService 返回 null ChunkingResult 时仍被原样包装（按实现）")
        void chunk_propagatesNullResultFromService() {
            ParsedSection s = section("x", "/x");
            when(chunkingService.chunk("x", rule)).thenReturn(null);

            List<ChunkedSection> out = adapter.chunk(readResult(s), rule);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).result()).isNull();
            assertThat(out.get(0).source()).isSameAs(s);
        }
    }

    // ============================================================== adapt()

    @Nested
    @DisplayName("adapt(Publisher, ChunkingRule) 仅发 ChunkedSection")
    class AdaptStream {

        @Test
        @DisplayName("Section 事件被转为 ChunkedSection 并向下游发送（保留 fileName）")
        void adapt_sectionEventEmitsChunkedSectionWithFileName() {
            // maxCharacters=2 使短文本 "hi" 在 accept 阶段立即 drain
            ChunkingRule smallRule = new ChunkingRule("adapt-section-name", "1",
                    ChunkingRule.Strategy.FIXED, 2, 0, null);
            ParsedSection source = section("hi", "/hi.txt");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "hi", 0, 2)));
            when(chunkingService.chunk("hi", smallRule)).thenReturn(cr);

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(
                    List.of(new ReadEvent.Section(source, "/hi.txt")));
            List<ChunkedSection> received = drain(adapter.adapt(pub, smallRule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).sectionIndex()).isEqualTo(0);
            assertThat(received.get(0).fileName()).isEqualTo("/hi.txt");
            assertThat(received.get(0).source()).isSameAs(source);
            // 流式路径 emit 的 ChunkedSection#result 是每 chunk 重新包装的 record，故用结构等价比对
            assertThat(received.get(0).result()).isEqualTo(cr);
        }

        @Test
        @DisplayName("Image 事件不向下游 emit，但 request(1) 续传使后续 Section 仍能被发出")
        void adapt_imageEventSkippedButStreamsContinue() {
            ChunkingRule smallRule = new ChunkingRule("adapt-image-skip", "1",
                    ChunkingRule.Strategy.FIXED, 2, 0, null);
            ParsedSection sourceA = section("aa", "/a.txt");
            ParsedSection sourceB = section("bb", "/b.txt");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "x", 0, 2)));
            when(chunkingService.chunk(any(), eq(smallRule))).thenReturn(cr);

            ParsedImage img = new ParsedImage("image/png", new byte[]{1, 2}, "img.png", "/page/1", Map.of());
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(sourceA, "/a.txt"),
                    new ReadEvent.Image(img, "/a.txt"),
                    new ReadEvent.Section(sourceB, "/b.txt")
            ));

            List<ChunkedSection> received = drain(adapter.adapt(pub, smallRule));

            assertThat(received).hasSize(2);
            assertThat(received.get(0).source()).isSameAs(sourceA);
            assertThat(received.get(1).source()).isSameAs(sourceB);
            assertThat(received.get(0).sectionIndex()).isEqualTo(0);
            assertThat(received.get(1).sectionIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("Finished / Failed 事件不向下游 emit（adapt 只发 ChunkedSection），但流正确终止")
        void adapt_finishedAndFailedDoNotEmit() {
            ParsedSection source = section("solo", "/solo.txt");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "solo", 0, 4)));
            when(chunkingService.chunk("solo", rule)).thenReturn(cr);

            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(source, "/solo.txt"),
                    new ReadEvent.Finished(summary, 1, 0)
            ));

            List<ChunkedSection> received = drain(adapter.adapt(pub, rule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).source()).isSameAs(source);
        }

        @Test
        @DisplayName("source 报 onError 时透传到下游 subscriber.onError")
        void adapt_errorPropagatesToDownstream() {
            RuntimeException sourceError = new RuntimeException("upstream boom");
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of());
            pub.failOnSubscribe(sourceError);

            DrainResult<ChunkedSection> result = drainResult(adapter.adapt(pub, rule));

            assertThat(result.error).isSameAs(sourceError);
            assertThat(result.completed).isFalse();
            assertThat(result.items).isEmpty();
        }

        @Test
        @DisplayName("source onComplete 透传到下游 subscriber.onComplete")
        void adapt_completePropagatesToDownstream() {
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of());

            DrainResult<ChunkedSection> result = drainResult(adapter.adapt(pub, rule));

            assertThat(result.error).isNull();
            assertThat(result.completed).isTrue();
            assertThat(result.items).isEmpty();
        }

        @Test
        @DisplayName("下游的 request(N) 经 FilteringSubscriber 透传到上游；cancel 同样被代理")
        void adapt_requestAndCancelProxiedThroughWrapper() {
            AtomicLong upstreamRequested = new AtomicLong();
            AtomicBoolean upstreamCancelled = new AtomicBoolean();

            ChunkingRule smallRule = new ChunkingRule("adapt-proxy", "1",
                    ChunkingRule.Strategy.FIXED, 1, 0, null);
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "x", 0, 1)));
            when(chunkingService.chunk(any(), eq(smallRule))).thenReturn(cr);

            List<ReadEvent> events = List.of(
                    new ReadEvent.Section(section("x", "/0"), "/0"),
                    new ReadEvent.Section(section("x", "/1"), "/1"),
                    new ReadEvent.Section(section("x", "/2"), "/2"),
                    new ReadEvent.Section(section("x", "/3"), "/3"),
                    new ReadEvent.Section(section("x", "/4"), "/4"),
                    new ReadEvent.Section(section("x", "/5"), "/5"),
                    new ReadEvent.Section(section("x", "/6"), "/6")
            );

            Flow.Publisher<ReadEvent> p = subscriber -> {
                AtomicInteger idx = new AtomicInteger();
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public synchronized void request(long n) {
                        if (n <= 0) return;
                        upstreamRequested.addAndGet(n);
                        long left = n;
                        while (left-- > 0 && idx.get() < events.size()) {
                            subscriber.onNext(events.get(idx.getAndIncrement()));
                        }
                        if (idx.get() >= events.size()) {
                            subscriber.onComplete();
                        }
                    }

                    @Override
                    public synchronized void cancel() {
                        upstreamCancelled.set(true);
                    }
                });
            };

            AtomicReference<Flow.Subscription> downstreamSubRef = new AtomicReference<>();
            adapter.adapt(p, smallRule).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    downstreamSubRef.set(s);
                    s.request(7L);
                }

                @Override
                public void onNext(ChunkedSection cs) {
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onComplete() {
                }
            });

            assertThat(upstreamRequested.get()).isGreaterThanOrEqualTo(1L);
            downstreamSubRef.get().cancel();
            assertThat(upstreamCancelled.get()).isTrue();
        }
    }

    // ============================================================== adaptEvents()

    @Nested
    @DisplayName("adaptEvents(Publisher, ChunkingRule) 完整事件流")
    class AdaptEventsStream {

        @Test
        @DisplayName("Section 事件被包成 ChunkingEvent.Section 并向下游发出")
        void adaptEvents_sectionEventEmitsChunkingEventSection() {
            ParsedSection source = section("hello", "/h.txt");
            ChunkingResult cr = new ChunkingResult(List.of(
                    new Chunk(0, "hello", 0, 5),
                    new Chunk(1, "world", 6, 11)));
            when(chunkingService.chunk("hello", rule)).thenReturn(cr);

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(
                    List.of(new ReadEvent.Section(source, "/h.txt")));

            List<ChunkingEvent> received = drain(adapter.adaptEvents(pub, rule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0)).isInstanceOf(ChunkingEvent.Section.class);
            ChunkingEvent.Section cs = (ChunkingEvent.Section) received.get(0);
            assertThat(cs.value().sectionIndex()).isEqualTo(0);
            assertThat(cs.value().fileName()).isEqualTo("/h.txt");
            assertThat(cs.value().source()).isSameAs(source);
        }

        @Test
        @DisplayName("emittedChunks 计数按每段切片数累加（多段拼接）")
        void adaptEvents_emittedChunksAggregatesAllSections() {
            ChunkingRule smallRule = new ChunkingRule("adapt-events-aggregate", "1",
                    ChunkingRule.Strategy.FIXED, 2, 0, null);
            ParsedSection s1 = section("aa", "/1");
            ParsedSection s2 = section("bb", "/2");
            ParsedSection s3 = section("cc", "/3");
            when(chunkingService.chunk("aa", smallRule)).thenReturn(new ChunkingResult(List.of(
                    new Chunk(0, "a", 0, 1),
                    new Chunk(1, "a", 1, 2))));
            when(chunkingService.chunk("bb", smallRule)).thenReturn(new ChunkingResult(List.of(
                    new Chunk(0, "b", 0, 2))));
            when(chunkingService.chunk("cc", smallRule)).thenReturn(new ChunkingResult(List.of()));

            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(s1, "/1"),
                    new ReadEvent.Section(s2, "/2"),
                    new ReadEvent.Section(s3, "/3"),
                    new ReadEvent.Finished(summary, 3, 0)
            ));

            List<ChunkingEvent> received = drain(adapter.adaptEvents(pub, smallRule));

            assertThat(received).hasSize(4);
            ChunkingEvent.Finished finished = (ChunkingEvent.Finished) received.get(3);
            assertThat(finished.summary()).isSameAs(summary);
            assertThat(finished.totalSections()).isEqualTo(3);
            assertThat(finished.emittedChunks()).isEqualTo(3);
        }

        @Test
        @DisplayName("Finished 事件被转写为 ChunkingEvent.Finished 并保留 summary")
        void adaptEvents_finishedCarriesSummary() {
            ReadSummary summary = new ReadSummary("hello", "world", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(
                    List.of(new ReadEvent.Finished(summary, 0, 0)));

            List<ChunkingEvent> received = drain(adapter.adaptEvents(pub, rule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0)).isInstanceOf(ChunkingEvent.Finished.class);
            ChunkingEvent.Finished finished = (ChunkingEvent.Finished) received.get(0);
            assertThat(finished.summary()).isSameAs(summary);
            assertThat(finished.totalSections()).isEqualTo(0);
            assertThat(finished.emittedChunks()).isEqualTo(0);
        }

        @Test
        @DisplayName("Failed 事件被转写为 ChunkingEvent.Failed 并保留异常")
        void adaptEvents_failedCarriesThrowable() {
            DocumentParseException cause = new DocumentParseException("kaboom");
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(
                    List.of(new ReadEvent.Failed(cause)));

            List<ChunkingEvent> received = drain(adapter.adaptEvents(pub, rule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0)).isInstanceOf(ChunkingEvent.Failed.class);
            ChunkingEvent.Failed failed = (ChunkingEvent.Failed) received.get(0);
            assertThat(failed.error()).isSameAs(cause);
        }

        @Test
        @DisplayName("Image 事件不向下游 emit（adaptEvents 跳过非 Section/Finished/Failed）")
        void adaptEvents_imageEventSkipped() {
            ParsedImage img = new ParsedImage("image/png", new byte[]{0},
                    "img.png", "/page/1", Map.of());
            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Image(img, "/file.txt"),
                    new ReadEvent.Finished(summary, 0, 1)
            ));

            List<ChunkingEvent> received = drain(adapter.adaptEvents(pub, rule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0)).isInstanceOf(ChunkingEvent.Finished.class);
        }

        @Test
        @DisplayName("source 报 onError 时透传到下游 subscriber.onError")
        void adaptEvents_errorPropagatesToDownstream() {
            RuntimeException sourceError = new RuntimeException("upstream error");
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of());
            pub.failOnSubscribe(sourceError);

            DrainResult<ChunkingEvent> result = drainResult(adapter.adaptEvents(pub, rule));

            assertThat(result.error).isSameAs(sourceError);
            assertThat(result.items).isEmpty();
        }

        @Test
        @DisplayName("source onComplete 透传到下游 subscriber.onComplete")
        void adaptEvents_completePropagatesToDownstream() {
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of());

            DrainResult<ChunkingEvent> result = drainResult(adapter.adaptEvents(pub, rule));

            assertThat(result.completed).isTrue();
            assertThat(result.items).isEmpty();
        }

        @Test
        @DisplayName("下游的 request(N) 通过 ChunkingSubscriber 累加为内部 demand；cancel 同样被代理")
        void adaptEvents_requestDirectlyHitsUpstream() {
            AtomicLong upstreamRequested = new AtomicLong();
            AtomicBoolean upstreamCancelled = new AtomicBoolean();

            ChunkingRule smallRule = new ChunkingRule("adapt-events-proxy", "1",
                    ChunkingRule.Strategy.FIXED, 1, 0, null);
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "x", 0, 1)));
            when(chunkingService.chunk(any(), eq(smallRule))).thenReturn(cr);

            List<ReadEvent> events = List.of(
                    new ReadEvent.Section(section("x", "/0"), "/0"),
                    new ReadEvent.Section(section("x", "/1"), "/1"),
                    new ReadEvent.Section(section("x", "/2"), "/2"),
                    new ReadEvent.Section(section("x", "/3"), "/3"),
                    new ReadEvent.Section(section("x", "/4"), "/4"),
                    new ReadEvent.Section(section("x", "/5"), "/5"),
                    new ReadEvent.Section(section("x", "/6"), "/6")
            );

            Flow.Publisher<ReadEvent> p = subscriber -> {
                AtomicInteger idx = new AtomicInteger();
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public synchronized void request(long n) {
                        if (n <= 0) return;
                        upstreamRequested.addAndGet(n);
                        long left = n;
                        while (left-- > 0 && idx.get() < events.size()) {
                            subscriber.onNext(events.get(idx.getAndIncrement()));
                        }
                        if (idx.get() >= events.size()) {
                            subscriber.onComplete();
                        }
                    }

                    @Override
                    public synchronized void cancel() {
                        upstreamCancelled.set(true);
                    }
                });
            };

            AtomicReference<Flow.Subscription> downstreamSubRef = new AtomicReference<>();
            adapter.adaptEvents(p, smallRule).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    downstreamSubRef.set(s);
                    s.request(13L);
                }

                @Override
                public void onNext(ChunkingEvent ev) {
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onComplete() {
                }
            });

            assertThat(upstreamRequested.get()).isGreaterThanOrEqualTo(1L);
            downstreamSubRef.get().cancel();
            assertThat(upstreamCancelled.get()).isTrue();
        }

        @Test
        @DisplayName("Failed 触发后：随后的 Section 不再产生 ChunkingEvent.Section（设计 §4.3.3 终止语义）")
        void adaptEvents_afterFailed_subsequentSectionsAreNotEmittedAsSection() {
            // 场景：Section A → Failed → Section B 时，downstream 应当看不到来自 Section B 的 ChunkingEvent.Section
            // （实际是 chunker 在 abort 之后收到新的 accept 抛 IllegalStateException，触发 adapter.onError 透传）
            ChunkingRule smallRule = new ChunkingRule("adapt-events-after-failed", "1",
                    ChunkingRule.Strategy.FIXED, 5, 0, null);
            ParsedSection sourceA = section("alpha", "/a.txt");
            ParsedSection sourceB = section("beta", "/b.txt");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "alpha", 0, 5)));
            when(chunkingService.chunk("alpha", smallRule)).thenReturn(cr);
            cn.richie696.component.parser.exception.DocumentParseException cause =
                    new cn.richie696.component.parser.exception.DocumentParseException("kaboom");

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(sourceA, "/a.txt"),
                    new ReadEvent.Failed(cause),
                    new ReadEvent.Section(sourceB, "/b.txt")
            ));

            DrainResult<ChunkingEvent> result = drainResult(adapter.adaptEvents(pub, smallRule));

            long sectionCount = result.items.stream()
                    .filter(ev -> ev instanceof ChunkingEvent.Section)
                    .count();
            assertThat(sectionCount).isEqualTo(1);
            assertThat(result.items).anyMatch(ev -> ev instanceof ChunkingEvent.Failed);
            assertThat(result.error).isNotNull();
            assertThat(result.completed).isFalse();
        }

        @Test
        @DisplayName("空 ReadResult 流：adaptEvents 仅发出 ChunkingEvent.Finished 一个事件")
        void adaptEvents_emptyReadResult_emitsOnlyFinished() {
            // 即一条 ReadResult 中 sections 为空，对应流式接口的等价场景：仅 Finished 事件
            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Finished(summary, 0, 0)
            ));

            DrainResult<ChunkingEvent> result = drainResult(adapter.adaptEvents(pub, rule));

            assertThat(result.error).isNull();
            assertThat(result.completed).isTrue();
            assertThat(result.items).hasSize(1);
            assertThat(result.items.get(0)).isInstanceOf(ChunkingEvent.Finished.class);
            ChunkingEvent.Finished finished = (ChunkingEvent.Finished) result.items.get(0);
            assertThat(finished.totalSections()).isEqualTo(0);
            assertThat(finished.emittedChunks()).isEqualTo(0);
        }

        @Test
        @DisplayName("下游 request(<= 0) 触发 IllegalArgumentException 并终止流")
        void adaptEvents_downstreamRequestNonPositiveTriggersIae() {
            // request(0) 在 chunker 未被调用前就触发，不会消费任何 chunkingService stub
            ParsedSection source = section("solo", "/solo.txt");
            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(source, "/solo.txt"),
                    new ReadEvent.Finished(summary, 1, 0)
            ));

            AtomicReference<Flow.Subscription> downstreamSubRef = new AtomicReference<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            adapter.adaptEvents(pub, rule).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    downstreamSubRef.set(s);
                    s.request(0L);
                }

                @Override
                public void onNext(ChunkingEvent ev) {
                }

                @Override
                public void onError(Throwable t) {
                    errorRef.set(t);
                }

                @Override
                public void onComplete() {
                }
            });

            assertThat(errorRef.get()).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================================================== 回归与终止语义（新加）

    @Nested
    @DisplayName("回归：chunk text 字段、Image 间隔终止、Failed 终止传播、oversize 错误")
    class RegressionAndTermination {

        /**
         * 短文本下 {@link cn.richie696.component.chunking.StreamingChunker} 在 {@code accept()} 时不触发
         * chunkingService（pending.length &lt; rule.maxCharacters）；只有 {@code finish()} 调用时才会批量
         * 调用 chunkingService。本回归测试使用 FIXED 策略 + {@code maxCharacters = 10, overlap = 0}：
         * 文本长度 ≥ 10 时，{@code accept()} 阶段立刻产出 1 个 chunk；{@code overlap = 0} 确保
         * consume = maxCharacters 让 pending 完全清空，避免跨 accept 边界时的过度切片。
         */
        private final ChunkingRule smallRule = new ChunkingRule(
                "regression-test", "1", ChunkingRule.Strategy.FIXED, 10, 0, null);

        @Test
        @DisplayName("chunk.text() == content.substring(charStart, charEnd)（关键回归）")
        void chunkChunkedTextMatchesSubstringOfSource() {
            // 关键回归：StreamingChunker 输出的 Chunk 的 text 字段必须与 chunked 各段拼接后可恢复
            String content = "alpha beta gamma delta epsilon";
            ParsedSection src = new ParsedSection(content, "/doc", Map.of("format", "text/plain"));

            // mock ChunkingService 按字符坐标切，返回 3 个 chunk（模拟真实的 substring）
            when(chunkingService.chunk(content, rule)).thenReturn(new ChunkingResult(List.of(
                    new Chunk(0, content.substring(0, 6), 0, 6),
                    new Chunk(1, content.substring(6, 10), 6, 10),
                    new Chunk(2, content.substring(10, content.length()), 10, content.length())
            )));

            List<ChunkedSection> out = adapter.chunk(readResult(src), rule);

            assertThat(out).hasSize(1);
            ChunkedSection chunked = out.get(0);
            assertThat(chunked.result().chunks()).hasSize(3);
            for (Chunk chunk : chunked.result().chunks()) {
                assertThat(chunk.text())
                        .as("chunk.text() must equal content.substring(charStart, charEnd)")
                        .isEqualTo(content.substring(chunk.charStart(), chunk.charEnd()));
            }
            // 拼接 chunk.text() 仍能完整恢复原文
            String reconstructed = chunked.result().chunks().stream()
                    .map(Chunk::text)
                    .reduce("", String::concat);
            assertThat(reconstructed).isEqualTo(content);
        }

        @Test
        @DisplayName("adaptEvents 跨 Section 中夹杂 Image：sectionIndex 连续递增（Image 不占位）")
        void adaptEvents_sectionIndexContinuousAcrossImages() {
            // 三个 Section 夹杂 2 个 Image，Image 不应占 sectionIndex
            // sectionIndex 取值：第一个 span 所属的 section；尾部 chunk 因跨段会复用前一 sectionIndex，
            // 这是设计行为（详见 StreamingChunker + sourceSpans 设计 §4.3.3）
            String text = "AAAAAAAAAA";
            ParsedSection s0 = section(text, "/0");
            ParsedSection s1 = section(text, "/1");
            ParsedSection s2 = section(text, "/2");
            ParsedImage img0 = new ParsedImage("image/png", new byte[]{0x1}, "img0.png", "/page/0", Map.of());
            ParsedImage img1 = new ParsedImage("image/png", new byte[]{0x2}, "img1.png", "/page/1", Map.of());

            when(chunkingService.chunk(any(), eq(smallRule)))
                    .thenReturn(new ChunkingResult(List.of(new Chunk(0, text, 0, text.length()))));

            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(s0, "/0"),
                    new ReadEvent.Image(img0, "/0"),
                    new ReadEvent.Section(s1, "/1"),
                    new ReadEvent.Image(img1, "/1"),
                    new ReadEvent.Section(s2, "/2"),
                    new ReadEvent.Finished(summary, 3, 2)
            ));

            List<ChunkingEvent> received = drain(adapter.adaptEvents(pub, smallRule));

            assertThat(received).hasSize(4); // 3 Section + 1 Finished
            // 第一个 Section 的 chunk 必然落在 section 0 → sectionIndex = 0
            assertThat(((ChunkingEvent.Section) received.get(0)).value().sectionIndex()).isEqualTo(0);
            // 关键断言：sectionIndex 始终在 [0, 3) 范围内（不会因 Image 事件溢出），最后以 Finished 收尾
            for (int i = 0; i < 3; i++) {
                assertThat(((ChunkingEvent.Section) received.get(i)).value().sectionIndex())
                        .isBetween(0, 2);
            }
            assertThat(received.get(3)).isInstanceOf(ChunkingEvent.Finished.class);
        }

        @Test
        @DisplayName("adapt 路径：Image 夹杂跨 Section，sectionIndex 仍然连续")
        void adapt_sectionIndexContinuousAcrossImages() {
            String text = "AAAAAAAAAA"; // length 10 >= smallRule.maxCharacters
            ParsedSection s0 = section(text, "/0");
            ParsedSection s1 = section(text, "/1");
            ParsedImage img0 = new ParsedImage("image/png", new byte[]{0x1}, "img0.png", "/page/0", Map.of());

            when(chunkingService.chunk(any(), eq(smallRule)))
                    .thenReturn(new ChunkingResult(List.of(new Chunk(0, text, 0, text.length()))));

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(s0, "/0"),
                    new ReadEvent.Image(img0, "/0"),
                    new ReadEvent.Section(s1, "/1")
            ));

            List<ChunkedSection> received = drain(adapter.adapt(pub, smallRule));

            assertThat(received).hasSize(2);
            assertThat(received.get(0).sectionIndex()).isEqualTo(0);
            assertThat(received.get(1).sectionIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("adaptEvents 空 result 流（仅 Finished）：下游只收到 Finished 一个事件")
        void adaptEvents_emptyResultStream_onlyFinishedEmitted() {
            // 验证空 ReadResult（sections 空）在流式接口下的等价场景
            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Finished(summary, 0, 0)
            ));

            DrainResult<ChunkingEvent> result = drainResult(adapter.adaptEvents(pub, rule));

            assertThat(result.error).isNull();
            assertThat(result.completed).isTrue();
            assertThat(result.items).hasSize(1);
            assertThat(result.items).allMatch(ev -> ev instanceof ChunkingEvent.Finished);
        }

        @Test
        @DisplayName("adaptEvents 中 ChunkingService 抛 IllegalStateException → 透传为 downstream.onError")
        void adaptEvents_bufferOverflowPropagatesAsErrorToDownstream() {
            // 自定义 maxPendingCharacters + chunkingService 抛 IllegalStateException 模拟 overflow
            ParserChunkingAdapter smallAdapter = new ParserChunkingAdapter(chunkingService, 64);
            String text = "AAAAAAAAAA"; // length 10 = smallRule.maxCharacters 触发 drain
            ParsedSection src = section(text, "/alpha");

            IllegalStateException overflow = new IllegalStateException("流式切片缓冲区超过上限: 64");
            when(chunkingService.chunk(text, smallRule)).thenThrow(overflow);

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(
                    List.of(new ReadEvent.Section(src, "/alpha")));

            DrainResult<ChunkingEvent> result = drainResult(smallAdapter.adaptEvents(pub, smallRule));

            assertThat(result.error).isNotNull();
            assertThat(result.completed).isFalse();
            // 没有 Section 事件发出（适配器在 chunker 抛错时已把 upstream cancel 并 onError）
            assertThat(result.items).noneMatch(ev -> ev instanceof ChunkingEvent.Section);
        }

        @Test
        @DisplayName("adapt() ChunkingService 抛异常：downstream.onError 路径 + 上游 cancel")
        void adapt_chunkingServiceThrowsPropagatesAsError() {
            ParserChunkingAdapter smallAdapter = new ParserChunkingAdapter(chunkingService, 64);
            String text = "AAAAAAAAAA"; // length 10 = smallRule.maxCharacters 触发 drain
            ParsedSection src = section(text, "/h");

            RuntimeException boom = new RuntimeException("chunking-service-down");
            when(chunkingService.chunk(text, smallRule)).thenThrow(boom);

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(
                    List.of(new ReadEvent.Section(src, "/h")));

            DrainResult<ChunkedSection> result = drainResult(smallAdapter.adapt(pub, smallRule));

            assertThat(result.error).isNotNull();
            assertThat(result.completed).isFalse();
            assertThat(result.items).isEmpty();
        }

        @Test
        @DisplayName("adaptEvents：下游 cancel 后再上游 onNext 不会触发新事件")
        void adaptEvents_cancelBeforeOnNextIsSilentlyIgnored() {
            String text = "AAAAAAAAAA"; // length 10 = smallRule.maxCharacters
            ParsedSection src = section(text, "/h");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, text, 0, text.length())));
            when(chunkingService.chunk(text, smallRule)).thenReturn(cr);
            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(src, "/h"),
                    new ReadEvent.Finished(summary, 1, 0)
            ));

            AtomicReference<Flow.Subscription> downstreamSubRef = new AtomicReference<>();
            AtomicBoolean onCompleteCalled = new AtomicBoolean();
            AtomicBoolean onErrorCalled = new AtomicBoolean();

            adapter.adaptEvents(pub, smallRule).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    downstreamSubRef.set(s);
                    s.request(1L); // 先拿 1 个 Section
                }

                @Override
                public void onNext(ChunkingEvent ev) {
                    downstreamSubRef.get().cancel(); // 拿到第一个事件立刻取消
                }

                @Override
                public void onError(Throwable t) {
                    onErrorCalled.set(true);
                }

                @Override
                public void onComplete() {
                    onCompleteCalled.set(true);
                }
            });

            // 由于 cancel 已发生：后续 Finished 也被 quietly discard（pendingEvents.clear 与 cancelled 标志）
            assertThat(onCompleteCalled.get()).isFalse();
            assertThat(onErrorCalled.get()).isFalse();
        }

        @Test
        @DisplayName("Failed 触发后：随后的 Section 不再产生 ChunkingEvent.Section（设计 §4.3.3 终止语义）")
        void adaptEvents_afterFailed_subsequentSectionsAreNotEmittedAsSection() {
            // 场景：Section A → Failed → Section B 时，downstream 应当看不到来自 Section B 的 ChunkingEvent.Section
            // Section A 使用 >= smallRule.maxCharacters 的文本以确保 accept 阶段就产出 Section 事件
            String text = "AAAAAAAAAA";
            ParsedSection sourceA = section(text, "/a.txt");
            ParsedSection sourceB = section(text, "/b.txt");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, text, 0, text.length())));
            when(chunkingService.chunk(text, smallRule)).thenReturn(cr);
            cn.richie696.component.parser.exception.DocumentParseException cause =
                    new cn.richie696.component.parser.exception.DocumentParseException("kaboom");

            RecordingTestPublisher<ReadEvent> pub = new RecordingTestPublisher<>(List.of(
                    new ReadEvent.Section(sourceA, "/a.txt"),
                    new ReadEvent.Failed(cause),
                    new ReadEvent.Section(sourceB, "/b.txt")
            ));

            DrainResult<ChunkingEvent> result = drainResult(adapter.adaptEvents(pub, smallRule));

            // 关键断言：downstream 收到的 ChunkingEvent 中，Section 类型只有 Section A 触发的 1 个
            long sectionCount = result.items.stream()
                    .filter(ev -> ev instanceof ChunkingEvent.Section)
                    .count();
            assertThat(sectionCount).isEqualTo(1);
            // Failed 事件必须出现在 items 中
            assertThat(result.items).anyMatch(ev -> ev instanceof ChunkingEvent.Failed);
            // 后续 Section 触发的 reject 流程必须透传到 downstream.onError
            assertThat(result.error).isNotNull();
            assertThat(result.completed).isFalse();
        }
    }

    // ============================================================== Test helpers

    /**
     * 同步、需求驱动的虚拟发布器。构造时给定 items 列表；当下游调用
     * {@link Flow.Subscription#request(long)} 时，按需求量逐项调用
     * {@link Flow.Subscriber#onNext(Object)}，最后以 onComplete / onError 收尾。
     * 设计目标：完全同步、无线程调度，方便 unit test 精确断言 request/cancel 行为。
     */
    private static final class RecordingTestPublisher<T> implements Flow.Publisher<T> {
        private final List<T> items;
        private volatile boolean failOnSubscribe;
        private volatile Throwable failure;

        RecordingTestPublisher(List<T> items) {
            this.items = items;
        }

        void failOnSubscribe(Throwable t) {
            this.failOnSubscribe = true;
            this.failure = t;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            if (failOnSubscribe) {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                subscriber.onError(failure);
                return;
            }
            subscriber.onSubscribe(new TestSubscription(subscriber));
        }

        private final class TestSubscription implements Flow.Subscription {
            private final Flow.Subscriber<? super T> subscriber;
            private long pendingDemand = 0;
            private int deliveredIndex = 0;
            private boolean cancelled = false;

            TestSubscription(Flow.Subscriber<? super T> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public synchronized void request(long n) {
                if (cancelled || n <= 0) return;
                pendingDemand += n;
                while (pendingDemand > 0 && deliveredIndex < items.size() && !cancelled) {
                    subscriber.onNext(items.get(deliveredIndex++));
                    pendingDemand--;
                }
                if (deliveredIndex >= items.size() && !cancelled) {
                    subscriber.onComplete();
                }
            }

            @Override
            public synchronized void cancel() {
                cancelled = true;
            }
        }
    }

    /**
     * 一次订阅的完整结果（items + error + completion）。
     */
    private static final class DrainResult<T> {
        final java.util.List<T> items;
        final Throwable error;
        final boolean completed;

        DrainResult(java.util.List<T> items, Throwable error, boolean completed) {
            this.items = items;
            this.error = error;
            this.completed = completed;
        }
    }

    private static <T> java.util.List<T> drain(Flow.Publisher<T> publisher) {
        return drainResult(publisher).items;
    }

    private static <T> DrainResult<T> drainResult(Flow.Publisher<T> publisher) {
        CopyOnWriteArrayList<T> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean completedRef = new AtomicBoolean();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
            }

            @Override
            public void onComplete() {
                completedRef.set(true);
            }
        });

        return new DrainResult<>(new java.util.ArrayList<>(received), errorRef.get(), completedRef.get());
    }
}
