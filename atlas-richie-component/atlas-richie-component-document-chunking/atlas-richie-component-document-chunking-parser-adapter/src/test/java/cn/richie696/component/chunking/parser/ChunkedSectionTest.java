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
import cn.richie696.component.parser.model.ParsedSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChunkedSection} 是 record，其唯一价值在于承载 {@code sectionIndex} /
 * {@code fileName} / 原始 {@link ParsedSection} 与切片结果 {@link ChunkingResult} 之间的对照关系。
 * 本类只覆盖 record 的字段直读与等价/散列契约。
 */
@DisplayName("ChunkedSection record 字段与等价契约")
class ChunkedSectionTest {

    @Test
    @DisplayName("record 各字段可直读")
    void record_exposesAllAccessors() {
        ParsedSection source = new ParsedSection("hello", "/a.txt", Map.of("format", "text/plain"));
        ChunkingResult result = new ChunkingResult(List.of(new Chunk(0, "hello", 0, 5)));

        ChunkedSection chunked = new ChunkedSection(7, "doc.txt", source, result);

        assertThat(chunked.sectionIndex()).isEqualTo(7);
        assertThat(chunked.fileName()).isEqualTo("doc.txt");
        assertThat(chunked.source()).isSameAs(source);
        assertThat(chunked.result()).isSameAs(result);
    }

    @Test
    @DisplayName("fileName 允许为 null（批量接口不感知来源文件）")
    void record_acceptsNullFileName() {
        ParsedSection source = new ParsedSection("x", "/x", Map.of());
        ChunkingResult result = new ChunkingResult(List.of());

        ChunkedSection chunked = new ChunkedSection(0, null, source, result);

        assertThat(chunked.fileName()).isNull();
    }

    @Test
    @DisplayName("record 等价合约：同字段同值视为相等")
    void record_equalityByFields() {
        ParsedSection source = new ParsedSection("hi", "/b", Map.of("format", "text/plain"));
        ChunkingResult result = new ChunkingResult(List.of(new Chunk(0, "hi", 0, 2)));

        ChunkedSection a = new ChunkedSection(2, "f.txt", source, result);
        ChunkedSection b = new ChunkedSection(2, "f.txt", source, result);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("record 不等性：任一字段不同则不相等")
    void record_inequalityWhenAnyFieldDiffers() {
        ParsedSection source = new ParsedSection("x", "/x", Map.of());
        ChunkingResult result = new ChunkingResult(List.of());

        ChunkedSection base = new ChunkedSection(0, "f", source, result);
        ChunkedSection diffIndex = new ChunkedSection(1, "f", source, result);
        ChunkedSection diffName = new ChunkedSection(0, "g", source, result);

        assertThat(base).isNotEqualTo(diffIndex);
        assertThat(base).isNotEqualTo(diffName);
        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("not a ChunkedSection");
    }
}
