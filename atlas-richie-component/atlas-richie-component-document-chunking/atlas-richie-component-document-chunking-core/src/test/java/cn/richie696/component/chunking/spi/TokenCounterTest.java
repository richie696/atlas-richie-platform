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
package cn.richie696.component.chunking.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenCounter SPI smoke check")
class TokenCounterTest {

    @Test
    @DisplayName("functional interface accepts a length-based lambda")
    void tokenCounter_lambda_countsTextLength() {
        TokenCounter counter = text -> text == null ? 0 : text.length();

        assertThat(counter.count("hello")).isEqualTo(5);
        assertThat(counter.count("")).isZero();
    }

    @Test
    @DisplayName("functional interface can encode a custom approximation")
    void tokenCounter_lambda_canUseCustomApproximation() {
        TokenCounter approx = text -> (int) Math.ceil(text.length() / 4.0);

        assertThat(approx.count("abcdefgh")).isEqualTo(2);
        assertThat(approx.count("a")).isEqualTo(1);
    }
}