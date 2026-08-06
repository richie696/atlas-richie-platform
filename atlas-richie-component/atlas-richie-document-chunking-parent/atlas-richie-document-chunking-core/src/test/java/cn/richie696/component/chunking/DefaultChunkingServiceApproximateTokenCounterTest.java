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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultChunkingService — approximate token counter")
class DefaultChunkingServiceApproximateTokenCounterTest {

    private final cn.richie696.component.chunking.spi.TokenCounter counter = DefaultChunkingService.approximateTokenCounter();

    @Test
    @DisplayName("Chinese characters count one token each")
    void chinese_countsOneTokenPerCharacter() {
        assertThat(counter.count("你好世界")).isEqualTo(4);
    }

    @Test
    @DisplayName("Latin runs count at roughly four characters per token")
    void latin_countsByFourCharacters() {
        assertThat(counter.count("hello")).isEqualTo(2);
    }

    @Test
    @DisplayName("mixed Latin and Chinese text counts each script correctly")
    void mixedText_countsBothScripts() {
        assertThat(counter.count("hi 你好")).isEqualTo(3);
    }

    @Test
    @DisplayName("empty, null, and whitespace-only text count zero tokens")
    void emptyNullAndWhitespace_countZero() {
        assertThat(counter.count("")).isZero();
        assertThat(counter.count(null)).isZero();
        assertThat(counter.count("   ")).isZero();
    }

    @Test
    @DisplayName("single Latin characters count at least one token")
    void singleCharacter_countsOneToken() {
        assertThat(counter.count("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("punctuation does not add tokens")
    void punctuation_countsZeroTokens() {
        assertThat(counter.count("！？。")).isZero();
    }
}
