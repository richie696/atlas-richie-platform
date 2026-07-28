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

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.parser.model.ParsedSection;
import cn.richie696.component.parser.model.ReadSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChunkingEvent} 是 sealed 接口，仅暴露三个 record 子类型：
 * <ul>
 *   <li>{@link ChunkingEvent.Section} — 单段切片完成</li>
 *   <li>{@link ChunkingEvent.Finished} — 文档完成（含汇总 + 计数）</li>
 *   <li>{@link ChunkingEvent.Failed} — 文档失败（含异常）</li>
 * </ul>
 * sealed 封闭性由编译器保证，不做运行时检查；本测试只覆盖 record 字段与等价契约。
 */
@DisplayName("ChunkingEvent sealed 接口与 record 子类型")
class ChunkingEventTest {

    @Test
    @DisplayName("Section 子类型可被实例化并保留 ChunkedSection 值")
    void section_carriesChunkedSectionValue() {
        ParsedSection source = new ParsedSection("hi", "/h.txt", Map.of("format", "text/plain"));
        ChunkingResult result = new ChunkingResult(List.of(new Chunk(0, "hi", 0, 2)));
        ChunkedSection chunked = new ChunkedSection(0, "h.txt", source, result);

        ChunkingEvent.Section event = new ChunkingEvent.Section(chunked);

        assertThat(event.value()).isSameAs(chunked);
    }

    @Test
    @DisplayName("Section record 相等合约：value 决定等价")
    void section_recordEquality() {
        ParsedSection source = new ParsedSection("hi", "/h.txt", Map.of("format", "text/plain"));
        ChunkingResult result = new ChunkingResult(List.of(new Chunk(0, "hi", 0, 2)));
        ChunkedSection chunked = new ChunkedSection(0, "h.txt", source, result);

        ChunkingEvent.Section a = new ChunkingEvent.Section(chunked);
        ChunkingEvent.Section b = new ChunkingEvent.Section(chunked);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Nested
    @DisplayName("Finished 子类型")
    class FinishedTests {

        @Test
        @DisplayName("字段直读保留 summary / totalSections / emittedChunks")
        void finished_exposesAllAccessors() {
            ReadSummary summary = new ReadSummary("title", "author", Map.of("format", "text/plain"));

            ChunkingEvent.Finished event = new ChunkingEvent.Finished(summary, 10, 42);

            assertThat(event.summary()).isSameAs(summary);
            assertThat(event.totalSections()).isEqualTo(10);
            assertThat(event.emittedChunks()).isEqualTo(42);
        }

        @Test
        @DisplayName("record 等价合约")
        void finished_recordEquality() {
            ReadSummary summary = new ReadSummary("t", "a", Map.of("format", "text/plain"));
            ChunkingEvent.Finished a = new ChunkingEvent.Finished(summary, 1, 2);
            ChunkingEvent.Finished b = new ChunkingEvent.Finished(summary, 1, 2);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("emittedChunks 不同时视为不等")
        void finished_inequalityByEmittedChunks() {
            ReadSummary summary = new ReadSummary("t", "a", Map.of());
            ChunkingEvent.Finished a = new ChunkingEvent.Finished(summary, 1, 2);
            ChunkingEvent.Finished b = new ChunkingEvent.Finished(summary, 1, 3);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("Failed 子类型")
    class FailedTests {

        @Test
        @DisplayName("字段直读保留 Throwable")
        void failed_exposesThrowable() {
            RuntimeException cause = new RuntimeException("boom");

            ChunkingEvent.Failed event = new ChunkingEvent.Failed(cause);

            assertThat(event.error()).isSameAs(cause);
            assertThat(event.error().getMessage()).isEqualTo("boom");
        }

        @Test
        @DisplayName("record 等价合约：error 决定等价")
        void failed_recordEquality() {
            RuntimeException cause = new RuntimeException("x");
            ChunkingEvent.Failed a = new ChunkingEvent.Failed(cause);
            ChunkingEvent.Failed b = new ChunkingEvent.Failed(cause);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("不同 cause 视为不等")
        void failed_inequalityByCause() {
            ChunkingEvent.Failed a = new ChunkingEvent.Failed(new RuntimeException("a"));
            ChunkingEvent.Failed b = new ChunkingEvent.Failed(new RuntimeException("b"));

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Test
    @DisplayName("不同子类型 record 之间互不相等（即使字段数相同）")
    void differentSubtypesAreNotEqual() {
        ChunkedSection chunked = new ChunkedSection(0, null,
                new ParsedSection("x", "/x", Map.of()),
                new ChunkingResult(List.of()));
        ChunkingEvent.Section section = new ChunkingEvent.Section(chunked);

        ChunkingEvent.Finished finished = new ChunkingEvent.Finished(
                new ReadSummary("t", "a", Map.of()), 0, 0);

        assertThat(section).isNotEqualTo(finished);
    }

    @Test
    @DisplayName("factory: recursiveRule 仅作为 build 契约占位（不参与 record 等价）")
    void record_buildsViaExplicitConstructor() {
        // 仅占位：验证 ChunkingRule 在子模块依赖中可见
        ChunkingRule rule = ChunkingRule.recursiveDefaults(100, 10);
        assertThat(rule.strategy()).isEqualTo(ChunkingRule.Strategy.RECURSIVE);
    }
}
