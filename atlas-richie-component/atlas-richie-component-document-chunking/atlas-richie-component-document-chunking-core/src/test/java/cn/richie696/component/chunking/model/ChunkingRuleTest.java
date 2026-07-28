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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChunkingRule record + Strategy enum + recursiveDefaults")
class ChunkingRuleTest {

    @Test
    @DisplayName("Strategy enum exposes nine ordered values")
    void strategy_enum_declaresAllNineValues() {
        assertThat(ChunkingRule.Strategy.values())
                .containsExactly(
                        ChunkingRule.Strategy.FIXED,
                        ChunkingRule.Strategy.RECURSIVE,
                        ChunkingRule.Strategy.TOKEN,
                        ChunkingRule.Strategy.PARAGRAPH,
                        ChunkingRule.Strategy.SENTENCE,
                        ChunkingRule.Strategy.MARKDOWN,
                        ChunkingRule.Strategy.HTML,
                        ChunkingRule.Strategy.PAGE,
                        ChunkingRule.Strategy.SEMANTIC);
    }

    @Test
    @DisplayName("blank ruleId is rejected")
    void chunkingRule_blankRuleId_throws() {
        assertThatThrownBy(() -> new ChunkingRule(" ", "1", ChunkingRule.Strategy.RECURSIVE, 10, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("blank version is rejected")
    void chunkingRule_blankVersion_throws() {
        assertThatThrownBy(() -> new ChunkingRule("r", "", ChunkingRule.Strategy.RECURSIVE, 10, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null strategy is rejected")
    void chunkingRule_nullStrategy_throws() {
        assertThatThrownBy(() -> new ChunkingRule("r", "1", null, 10, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxCharacters <= 0 is rejected")
    void chunkingRule_nonPositiveMax_throws() {
        assertThatThrownBy(() -> new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, -1, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("negative overlap is rejected")
    void chunkingRule_negativeOverlap_throws() {
        assertThatThrownBy(() -> new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, 10, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("overlap >= maxCharacters is rejected")
    void chunkingRule_overlapGreaterThanOrEqualMax_throws() {
        assertThatThrownBy(() -> new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, 10, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, 10, 11, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null separators are replaced by the canonical seven-item default")
    void chunkingRule_nullSeparators_usesDefaults() {
        ChunkingRule rule = new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, 10, 0, null);

        assertThat(rule.separators())
                .containsExactly("\n\n", "\n", "。", "！", "？", ". ", " ");
    }

    @Test
    @DisplayName("supplied separators are copied into an immutable list")
    void chunkingRule_suppliedSeparators_areCopiedAndImmutable() {
        List<String> source = new ArrayList<>(Arrays.asList("AAA", "BBB"));
        ChunkingRule rule = new ChunkingRule("r", "1", ChunkingRule.Strategy.RECURSIVE, 10, 0, source);

        source.clear();
        assertThat(rule.separators()).containsExactly("AAA", "BBB");
        assertThatThrownBy(() -> rule.separators().add("CCC"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("recursiveDefaults builds a RECURSIVE rule with stable id/version and the supplied max/overlap")
    void chunkingRule_recursiveDefaults_producesRecursiveRule() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(512, 64);

        assertThat(rule.strategy()).isEqualTo(ChunkingRule.Strategy.RECURSIVE);
        assertThat(rule.ruleId()).isEqualTo("default-recursive");
        assertThat(rule.version()).isEqualTo("1");
        assertThat(rule.maxCharacters()).isEqualTo(512);
        assertThat(rule.overlapCharacters()).isEqualTo(64);
        assertThat(rule.separators()).isNotEmpty();
    }

    @Test
    @DisplayName("record equality compares every component")
    void chunkingRule_recordEqualityAndAccessors() {
        ChunkingRule a = new ChunkingRule("r", "1", ChunkingRule.Strategy.SENTENCE, 20, 2, null);
        ChunkingRule b = new ChunkingRule("r", "1", ChunkingRule.Strategy.SENTENCE, 20, 2, null);
        ChunkingRule c = new ChunkingRule("r", "1", ChunkingRule.Strategy.SENTENCE, 21, 2, null);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.strategy()).isEqualTo(ChunkingRule.Strategy.SENTENCE);
    }
}