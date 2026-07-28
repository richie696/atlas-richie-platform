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

import cn.richie696.component.parser.model.ParsedSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link SourceSpan} 是 record，承载连续文档流中一个 Chunk 所覆盖的 parser section 区间，
 * 字符坐标相对于该 section 自身的文本。覆盖：
 * <ul>
 *   <li>record 字段直读</li>
 *   <li>record 等价 / 散列 / toString 契约</li>
 *   <li>构造器非法参数守卫（sectionIndex / charStart / charEnd / section 坐标）</li>
 * </ul>
 */
@DisplayName("SourceSpan record 字段与构造器守卫")
class SourceSpanTest {

    private static ParsedSection section(String text) {
        return new ParsedSection(text, "/doc", Map.of("format", "text/plain"));
    }

    @Test
    @DisplayName("record 字段直读")
    void record_exposesAllAccessors() {
        ParsedSection source = section("hello world");
        SourceSpan span = new SourceSpan(3, source, 0, 5);

        assertThat(span.sectionIndex()).isEqualTo(3);
        assertThat(span.section()).isSameAs(source);
        assertThat(span.charStart()).isEqualTo(0);
        assertThat(span.charEnd()).isEqualTo(5);
    }

    @Test
    @DisplayName("record 等价合约：所有字段相同则视为相等")
    void record_equalityByFields() {
        ParsedSection source = section("hello");
        SourceSpan a = new SourceSpan(0, source, 0, 5);
        SourceSpan b = new SourceSpan(0, source, 0, 5);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("record 不等性：任一字段不同则不等")
    void record_inequalityWhenAnyFieldDiffers() {
        ParsedSection s = section("hello");
        SourceSpan base = new SourceSpan(0, s, 0, 5);
        SourceSpan diffIndex = new SourceSpan(1, s, 0, 5);
        SourceSpan diffStart = new SourceSpan(0, s, 1, 5);
        SourceSpan diffEnd = new SourceSpan(0, s, 0, 4);
        SourceSpan diffSection = new SourceSpan(0, section("other"), 0, 5);

        assertThat(base).isNotEqualTo(diffIndex);
        assertThat(base).isNotEqualTo(diffStart);
        assertThat(base).isNotEqualTo(diffEnd);
        assertThat(base).isNotEqualTo(diffSection);
        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("not a SourceSpan");
    }

    @Test
    @DisplayName("toString 包含字段名便于排错")
    void record_toStringContainsFieldNames() {
        SourceSpan span = new SourceSpan(2, section("hello"), 0, 5);

        assertThat(span.toString()).contains("sectionIndex").contains("charStart").contains("charEnd");
    }

    @Test
    @DisplayName("构造器：sectionIndex 为负抛 IAE")
    void constructor_negativeSectionIndexRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceSpan(-1, section("hello"), 0, 5));
    }

    @Test
    @DisplayName("构造器：null section 抛 IAE")
    void constructor_nullSectionRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceSpan(0, null, 0, 5));
    }

    @Test
    @DisplayName("构造器：charStart 为负抛 IAE")
    void constructor_negativeCharStartRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceSpan(0, section("hello"), -1, 5));
    }

    @Test
    @DisplayName("构造器：charEnd < charStart 抛 IAE")
    void constructor_charEndBeforeCharStartRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceSpan(0, section("hello"), 5, 3));
    }

    @Test
    @DisplayName("构造器：charEnd 超出 section 文本长度抛 IAE")
    void constructor_charEndBeyondSectionTextRejected() {
        // section 长度为 5，charEnd = 6 越界
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceSpan(0, section("hello"), 0, 6));
    }

    @Test
    @DisplayName("构造器：charStart == charEnd 是合法的空跨度")
    void constructor_emptySpanAccepted() {
        // 0 == 0 是合法的左闭右开空区间
        SourceSpan span = new SourceSpan(0, section("hello"), 0, 0);

        assertThat(span.charStart()).isEqualTo(0);
        assertThat(span.charEnd()).isEqualTo(0);
    }

    @Test
    @DisplayName("构造器：charEnd == section.length() 边界合法")
    void constructor_charEndAtSectionBoundaryAccepted() {
        SourceSpan span = new SourceSpan(0, section("hello"), 0, 5);

        assertThat(span.charEnd()).isEqualTo(5);
    }
}
