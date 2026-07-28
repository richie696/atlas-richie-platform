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
import cn.richie696.component.parser.model.ParsedSection;
import cn.richie696.component.parser.model.ReadEvent;
import cn.richie696.component.parser.model.ReadResult;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ParserChunkingAdapter} 构造器契约的纯单元测试，关注：
 * <ul>
 *   <li>{@code chunkingService == null} 必须抛出 {@link NullPointerException}</li>
 *   <li>{@code maxPendingCharacters <= 0} 必须抛出 {@link IllegalArgumentException}</li>
 *   <li>单参构造器的 {@code maxPendingCharacters} 默认值（与源码常数一致）</li>
 *   <li>合法构造器实例可正常完成最小业务路径（验证默认值无副作用）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ParserChunkingAdapter 构造器契约")
class ParserChunkingAdapterConstructorTest {

    /**
     * 默认 {@code maxPendingCharacters} 的预期值。源码 {@code ParserChunkingAdapter#ParserChunkingAdapter(ChunkingService)}
     * 写死为 {@code 8_192}；本测试断言构造后的实例能以该默认值工作，
     * 一旦源码默认值变更需要同步更新本常量。
     */
    private static final int EXPECTED_DEFAULT_MAX_PENDING_CHARACTERS = 8_192;

    @Mock
    private ChunkingService chunkingService;

    private static ParsedSection section(String text) {
        return new ParsedSection(text, "/doc", Map.of("format", "text/plain"));
    }

    private static ReadResult readResult(ParsedSection... sections) {
        return new ReadResult("title", "author", List.of(sections), List.of(), Map.of("format", "text/plain"));
    }

    // ============================================================== 参数校验

    @Nested
    @DisplayName("参数校验 (NPE / IAE)")
    class ArgumentValidation {

        @Test
        @DisplayName("单参构造器：null chunkingService 抛 NPE")
        void singleArg_nullServiceThrowsNpe() {
            assertThatNullPointerException().isThrownBy(() -> new ParserChunkingAdapter(null));
        }

        @Test
        @DisplayName("双参构造器：null chunkingService 抛 NPE")
        void twoArg_nullServiceThrowsNpe() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ParserChunkingAdapter(null, 1024));
        }

        @Test
        @DisplayName("双参构造器：maxPendingCharacters = -1 抛 IAE")
        void twoArg_negativeMaxPendingThrowsIae() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ParserChunkingAdapter(chunkingService, -1));
        }

        @Test
        @DisplayName("双参构造器：maxPendingCharacters = 0 抛 IAE（边界）")
        void twoArg_zeroMaxPendingThrowsIae() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ParserChunkingAdapter(chunkingService, 0));
        }

        @Test
        @DisplayName("双参构造器：maxPendingCharacters = 1 是合法下界")
        void twoArg_oneMaxPendingAccepted() {
            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService, 1);

            assertThat(adapter).isNotNull();
        }

        @Test
        @DisplayName("双参构造器：maxPendingCharacters = Integer.MAX_VALUE 合法")
        void twoArg_maxValueMaxPendingAccepted() {
            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService, Integer.MAX_VALUE);

            assertThat(adapter).isNotNull();
        }
    }

    // ============================================================== 默认值

    @Nested
    @DisplayName("默认 maxPendingCharacters 行为")
    class DefaultMaxPending {

        @Test
        @DisplayName("单参构造器默认 maxPendingCharacters = 8_192（与源码常量一致）")
        void singleArg_defaultMaxPendingIsEightThousandOneHundredNinetyTwo() {
            // 间接验证：构造一个超大的 section 让 StreamingChunker 触发 oversize 异常需要
            // maxPendingCharacters 比通常情况更小。此处通过反射读取私有字段以避免 any 副作用。
            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService);

            int actual = readMaxPendingCharacters(adapter);
            assertThat(actual).isEqualTo(EXPECTED_DEFAULT_MAX_PENDING_CHARACTERS);
        }

        @Test
        @DisplayName("单参构造器：默认构造的适配器能完成最小 chunk() 路径")
        void singleArg_defaultAdapterWorksOnChunkPath() {
            ParsedSection s = section("hello world");
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "hello world", 0, 11)));
            when(chunkingService.chunk("hello world", ChunkingRule.recursiveDefaults(100, 10)))
                    .thenReturn(cr);

            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService);
            List<ChunkedSection> out = adapter.chunk(readResult(s),
                    ChunkingRule.recursiveDefaults(100, 10));

            assertThat(out).hasSize(1);
            verify(chunkingService).chunk("hello world", ChunkingRule.recursiveDefaults(100, 10));
        }

        @Test
        @DisplayName("双参构造器：自定义 maxPendingCharacters 透传给内部 StreamingChunker")
        void twoArg_customMaxPendingPropagated() {
            // 构造后通过反射读取私有字段，验证 16,384 透传成功
            int custom = 16_384;
            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService, custom);

            assertThat(readMaxPendingCharacters(adapter)).isEqualTo(custom);
        }

        @Test
        @DisplayName("双参构造器：自定义 maxPendingCharacters 比 rule.maxCharacters 小时，取大值保证容纳")
        void twoArg_maxPendingBelowRuleMaxAcceptsButBumpsToRuleMax() {
            // rule.maxCharacters() = 100，maxPendingCharacters = 10 → 实际为 max(10, 100) = 100
            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService, 10);

            // 内部不会保存到字段，仅保证 chunker 构造时取大值；这里用 chunk() 间接验证不抛
            ParsedSection s = section("hello");
            when(chunkingService.chunk(any(), eq(ChunkingRule.recursiveDefaults(100, 10))))
                    .thenReturn(new ChunkingResult(List.of(new Chunk(0, "hello", 0, 5))));

            List<ChunkedSection> out = adapter.chunk(readResult(s), ChunkingRule.recursiveDefaults(100, 10));

            assertThat(out).hasSize(1);
        }

        @Test
        @DisplayName("自定义 maxPendingCharacters 在 adaptEvents 路径上不引入额外驱动副作用")
        void twoArg_adaptEventsPathWorksWithCustomMaxPending() {
            ParsedSection src = section("hello");
            ChunkingRule rule = ChunkingRule.recursiveDefaults(100, 10);
            ChunkingResult cr = new ChunkingResult(List.of(new Chunk(0, "hello", 0, 5)));
            when(chunkingService.chunk("hello", rule)).thenReturn(cr);

            ParserChunkingAdapter adapter = new ParserChunkingAdapter(chunkingService, 32_000);
            ManualPublisher<ReadEvent> source = new ManualPublisher<>(List.of(
                    new ReadEvent.Section(src, "/doc")));
            List<ChunkingEvent> received = drain(adapter.adaptEvents(source, rule));

            assertThat(received).hasSize(1);
            assertThat(received.get(0)).isInstanceOf(ChunkingEvent.Section.class);
        }
    }

    // ============================================================== 反射读字段

    private static int readMaxPendingCharacters(ParserChunkingAdapter adapter) {
        try {
            java.lang.reflect.Field field = ParserChunkingAdapter.class.getDeclaredField("maxPendingCharacters");
            field.setAccessible(true);
            return field.getInt(adapter);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法读取 ParserChunkingAdapter#maxPendingCharacters 字段", e);
        }
    }

    // ============================================================== 助手

    /**
     * 同步、按需求发放的最小发布器，与已有 {@code RecordingTestPublisher} 解耦，
     * 专用于构造器测试的轻量场景。
     */
    private static final class ManualPublisher<T> implements Flow.Publisher<T> {
        private final List<T> items;

        ManualPublisher(List<T> items) {
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

    private static <T> List<T> drain(Flow.Publisher<T> publisher) {
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

        return new java.util.ArrayList<>(received);
    }
}
