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
import cn.richie696.component.chunking.DefaultChunkingService;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.parser.exception.DocumentParseException;
import cn.richie696.component.parser.model.ParsedImage;
import cn.richie696.component.parser.model.ParsedSection;
import cn.richie696.component.parser.model.ReadEvent;
import cn.richie696.component.parser.model.ReadResult;
import cn.richie696.component.parser.model.ReadSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 上下文集成测试：真实装配 {@link DefaultChunkingService}，
 * 通过 {@link ParserChunkingAdapter} 验证 {@code chunk} / {@code adapt} / {@code adaptEvents}
 * 在 {@code SpringBootTest} 下端到端可用。
 * 该测试由 Failsafe 在 verify 阶段执行（文件名以 {@code IT} 结尾）。
 */
@SpringBootTest(classes = ParserChunkingAdapterIT.TestConfig.class)
@DisplayName("ParserChunkingAdapter Spring Boot 集成")
class ParserChunkingAdapterIT {

    @Autowired
    private ParserChunkingAdapter adapter;

    @Test
    @DisplayName("Spring 上下文将 DefaultChunkingService 注入到 ParserChunkingAdapter")
    void spring_wiresDefaultChunkingServiceIntoAdapter() {
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("chunk() 在真实 ChunkingService 上将每段文本切片并产出 N 个 ChunkedSection")
    void chunk_endToEndWithRealService() {
        ParsedSection s1 = new ParsedSection("alpha alpha alpha\nbeta beta", "/doc/0",
                Map.of("format", "text/plain"));
        ParsedSection s2 = new ParsedSection("gamma gamma gamma\ndelta delta", "/doc/1",
                Map.of("format", "text/plain"));
        ReadResult result = new ReadResult(
                "title", "author",
                List.of(s1, s2),
                List.of(),
                Map.of("format", "text/plain"));
        ChunkingRule rule = ChunkingRule.recursiveDefaults(20, 5);

        List<ChunkedSection> out = adapter.chunk(result, rule);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).sectionIndex()).isEqualTo(0);
        assertThat(out.get(1).sectionIndex()).isEqualTo(1);
        assertThat(out.get(0).source()).isSameAs(s1);
        assertThat(out.get(1).source()).isSameAs(s2);
        assertThat(out.get(0).fileName()).isNull(); // 批量接口不感知文件
        assertThat(out.get(0).result().chunks()).isNotEmpty();
        assertThat(out.get(1).result().chunks()).isNotEmpty();
    }

    @Test
    @DisplayName("adapt() 在真实服务下将 Section 事件转为 ChunkedSection，Finished 不发但流正确终止")
    void adapt_endToEndWithRealService() {
        ParsedSection src = new ParsedSection("hello world", "/x.txt",
                Map.of("format", "text/plain"));
        ChunkingRule rule = new ChunkingRule("adapt-it-e2e", "1",
                ChunkingRule.Strategy.FIXED, 11, 0, null);

        ReadSummary summary = new ReadSummary("doc", "alice", Map.of("format", "text/plain"));
        SyncPublisher<ReadEvent> source = new SyncPublisher<>(List.of(
                new ReadEvent.Section(src, "/x.txt"),
                new ReadEvent.Finished(summary, 1, 0)
        ));

        DrainResult<ChunkedSection> result = drain(adapter.adapt(source, rule));

        assertThat(result.error).isNull();
        assertThat(result.completed).isTrue();
        assertThat(result.items).hasSize(1);
        assertThat(result.items.get(0).fileName()).isEqualTo("/x.txt");
        assertThat(result.items.get(0).source()).isSameAs(src);
        assertThat(result.items.get(0).result().chunks()).isNotEmpty();
    }

    @Test
    @DisplayName("adaptEvents() 在真实服务下输出 Section + Finished，且 emittedChunks 等于各段 chunk 总和")
    void adaptEvents_endToEndWithRealService() {
        ParsedSection s1 = new ParsedSection("aaa bbb ccc", "/1", Map.of("format", "text/plain"));
        ParsedSection s2 = new ParsedSection("ddd eee fff", "/2", Map.of("format", "text/plain"));
        ChunkingRule rule = new ChunkingRule("adapt-events-it-e2e", "1",
                ChunkingRule.Strategy.FIXED, 11, 0, null);

        ReadSummary summary = new ReadSummary("doc", "alice", Map.of("format", "text/plain"));
        SyncPublisher<ReadEvent> source = new SyncPublisher<>(List.of(
                new ReadEvent.Section(s1, "/1"),
                new ReadEvent.Section(s2, "/2"),
                new ReadEvent.Finished(summary, 2, 0)
        ));

        DrainResult<ChunkingEvent> result = drain(adapter.adaptEvents(source, rule));

        assertThat(result.error).isNull();
        assertThat(result.completed).isTrue();
        assertThat(result.items).hasSize(3);
        assertThat(result.items.get(0)).isInstanceOf(ChunkingEvent.Section.class);
        assertThat(result.items.get(1)).isInstanceOf(ChunkingEvent.Section.class);
        ChunkingEvent.Finished finished = (ChunkingEvent.Finished) result.items.get(2);
        assertThat(finished.summary()).isSameAs(summary);
        assertThat(finished.totalSections()).isEqualTo(2);
        assertThat(finished.emittedChunks()).isGreaterThan(0);
        // Section 间索引连续
        assertThat(((ChunkingEvent.Section) result.items.get(0)).value().sectionIndex()).isEqualTo(0);
        assertThat(((ChunkingEvent.Section) result.items.get(1)).value().sectionIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("adaptEvents() 在真实服务下输出 Failed（含异常），并完成流")
    void adaptEvents_emitsFailedWithRealService() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(50, 5);
        DocumentParseException cause = new DocumentParseException("parse exploded");

        SyncPublisher<ReadEvent> source = new SyncPublisher<>(List.of(
                new ReadEvent.Failed(cause)
        ));

        DrainResult<ChunkingEvent> result = drain(adapter.adaptEvents(source, rule));

        assertThat(result.error).isNull();
        assertThat(result.completed).isTrue();
        assertThat(result.items).hasSize(1);
        ChunkingEvent.Failed failed = (ChunkingEvent.Failed) result.items.get(0);
        assertThat(failed.error()).isSameAs(cause);
    }

    @Test
    @DisplayName("adaptEvents() 跳过 Image 事件并将后续 Finished 正常发出")
    void adaptEvents_skipsImageEventsWithRealService() {
        ParsedImage img = new ParsedImage("image/png", new byte[]{0x1, 0x2},
                "img.png", "/page/1", Map.of());
        ChunkingRule rule = ChunkingRule.recursiveDefaults(40, 5);
        ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));

        SyncPublisher<ReadEvent> source = new SyncPublisher<>(List.of(
                new ReadEvent.Image(img, "/file.txt"),
                new ReadEvent.Finished(summary, 0, 1)
        ));

        DrainResult<ChunkingEvent> result = drain(adapter.adaptEvents(source, rule));

        assertThat(result.error).isNull();
        assertThat(result.items).hasSize(1);
        assertThat(result.items.get(0)).isInstanceOf(ChunkingEvent.Finished.class);
    }

    @Test
    @DisplayName("FIXED 策略 + 多 Section：chunk.text() == source.text().substring(charStart, charEnd) 不变量")
    void chunk_fixedStrategy_preservesTextRecoverability() {
        // 选取文本中无边界空格，避免 DefaultChunkingService#skipTrailingWhitespace 切掉空格
        ParsedSection s1 = new ParsedSection("OneTwoThreeFourFiveSixSevenEightNineTen",
                "/a", Map.of("format", "text/plain"));
        ParsedSection s2 = new ParsedSection("AlphaBetaGammaDeltaEpsilon",
                "/b", Map.of("format", "text/plain"));
        ReadResult result = new ReadResult(
                "title", "author",
                List.of(s1, s2),
                List.of(),
                Map.of("format", "text/plain"));
        ChunkingRule rule = new ChunkingRule(
                "fixed-test", "1", ChunkingRule.Strategy.FIXED, 10, 0, null);

        List<ChunkedSection> out = adapter.chunk(result, rule);

        assertThat(out).hasSize(2);
        for (ChunkedSection chunked : out) {
            for (cn.richie696.component.chunking.model.Chunk chunk : chunked.result().chunks()) {
                assertThat(chunk.text())
                        .as("FIXED 切片 chunk.text() 应 = content.substring(charStart, charEnd)")
                        .isEqualTo(chunked.source().text().substring(chunk.charStart(), chunk.charEnd()));
            }
        }
    }

    @Test
    @DisplayName("Section → Failed → Finished：下游收到 Section/Failed + Finished（终止语义）")
    void adaptEvents_sectionIsEmitted_onceAndFailed_thenFinished_terminatePropagation() {
        // FIXED 策略 + overlap=0 + 文本长度 = maxCharacters ⇒ accept 阶段正好产出 1 chunk
        String text = "AAAAAAAAAAAAAAAAAAAA"; // 20 A's = rule.maxCharacters
        ParsedSection src = new ParsedSection(text, "/x", Map.of("format", "text/plain"));
        ChunkingRule rule = new ChunkingRule(
                "fixed-test", "1", ChunkingRule.Strategy.FIXED, 20, 0, null);
        ReadSummary summary = new ReadSummary("doc", "alice", Map.of("format", "text/plain"));
        DocumentParseException cause = new DocumentParseException("parse error");

        SyncPublisher<ReadEvent> source = new SyncPublisher<>(List.of(
                new ReadEvent.Section(src, "/x"),
                new ReadEvent.Failed(cause),
                new ReadEvent.Finished(summary, 1, 1)
        ));

        DrainResult<ChunkingEvent> result = drain(adapter.adaptEvents(source, rule));

        assertThat(result.error).isNull();
        assertThat(result.completed).isTrue();
        // 至少 1 个 Section event + 1 个 Failed + 1 个 Finished = 至少 3
        assertThat(result.items.size()).isGreaterThanOrEqualTo(3);
        assertThat(result.items).anyMatch(ev -> ev instanceof ChunkingEvent.Section);
        assertThat(result.items).anyMatch(ev -> ev instanceof ChunkingEvent.Failed);
        ChunkingEvent.Failed failed = result.items.stream()
                .filter(ev -> ev instanceof ChunkingEvent.Failed)
                .map(ev -> (ChunkingEvent.Failed) ev)
                .findFirst().orElseThrow();
        assertThat(failed.error()).isSameAs(cause);
        assertThat(result.items).anyMatch(ev -> ev instanceof ChunkingEvent.Finished);
    }

    @Test
    @DisplayName("Failed 后紧跟 Section：下游仅收到 Section/Failed，被 onError 终结")
    void adaptEvents_failedTriggersErrorOnNextSectionArrival() {
        String text = "AAAAAAAAAAAAAAAAAAAA"; // 20 A's = rule.maxCharacters
        ParsedSection srcA = new ParsedSection(text, "/a", Map.of("format", "text/plain"));
        ParsedSection srcB = new ParsedSection(text, "/b", Map.of("format", "text/plain"));
        ChunkingRule rule = new ChunkingRule(
                "fixed-test", "1", ChunkingRule.Strategy.FIXED, 20, 0, null);
        DocumentParseException cause = new DocumentParseException("boom");

        SyncPublisher<ReadEvent> source = new SyncPublisher<>(List.of(
                new ReadEvent.Section(srcA, "/a"),
                new ReadEvent.Failed(cause),
                new ReadEvent.Section(srcB, "/b")
        ));

        DrainResult<ChunkingEvent> result = drain(adapter.adaptEvents(source, rule));

        // 关键语义：来自 Section B 的 Section 事件不应到达 downstream
        // Section B 触发的 chunker.accept 在 aborted chunker 上抛 IllegalStateException
        // 适配器把异常透传到 downstream.onError
        assertThat(result.error).isNotNull();
        assertThat(result.completed).isFalse();
        assertThat(result.items).anyMatch(ev -> ev instanceof ChunkingEvent.Failed);
    }

    @Test
    @DisplayName("Spring 上下文加载时间合理（< 5s sanity）")
    void spring_contextLoadsWithinFiveSeconds() {
        long start = System.nanoTime();
        assertThat(adapter).isNotNull();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(5_000L);
    }

    // ============================================================== Config

    @SpringBootConfiguration
    @Import(ParserChunkingAdapterIT.ParsedReaderConfig.class)
    static class TestConfig { }

    @Configuration
    static class ParsedReaderConfig {

        @Bean
        ChunkingService chunkingService() {
            return new DefaultChunkingService();
        }

        @Bean
        ParserChunkingAdapter parserChunkingAdapter(ChunkingService chunkingService) {
            return new ParserChunkingAdapter(chunkingService);
        }
    }

    // ============================================================== Helpers

    /** 同步、需求驱动的虚拟发布器，下游请求时按顺序发放 items 然后以 onComplete 收尾。 */
    private static final class SyncPublisher<T> implements Flow.Publisher<T> {
        private final List<T> items;

        SyncPublisher(List<T> items) {
            this.items = items;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private long pending = 0;
                private int delivered = 0;
                private boolean cancelled = false;

                @Override
                public synchronized void request(long n) {
                    if (cancelled || n <= 0) return;
                    pending += n;
                    while (pending > 0 && delivered < items.size() && !cancelled) {
                        subscriber.onNext(items.get(delivered++));
                        pending--;
                    }
                    if (delivered >= items.size() && !cancelled) {
                        subscriber.onComplete();
                    }
                }

                @Override
                public synchronized void cancel() {
                    cancelled = true;
                }
            });
        }
    }

    private static final class DrainResult<T> {
        final List<T> items;
        final Throwable error;
        final boolean completed;

        DrainResult(List<T> items, Throwable error, boolean completed) {
            this.items = items;
            this.error = error;
            this.completed = completed;
        }
    }

    private static <T> DrainResult<T> drain(Flow.Publisher<T> publisher) {
        CopyOnWriteArrayList<T> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean completedRef = new AtomicBoolean();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }
            @Override public void onNext(T item) {
                received.add(item);
            }
            @Override public void onError(Throwable t) {
                errorRef.set(t);
            }
            @Override public void onComplete() {
                completedRef.set(true);
            }
        });

        return new DrainResult<>(new ArrayList<>(received), errorRef.get(), completedRef.get());
    }
}
