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

import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.spi.TokenCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultChunkingService — constructor guards")
class DefaultChunkingServiceConstructorTest {

    private static final ChunkingRule RULE = ChunkingRule.recursiveDefaults(10, 0);
    private static final TokenCounter COUNTER = text -> 1;

    @Test
    @DisplayName("negative minimum chunk characters are rejected")
    void negativeMinimumCharacters_throwsIllegalArgument() {
        assertThatThrownBy(() -> new DefaultChunkingService(RULE, COUNTER, -1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("non-positive chunk document limits are rejected")
    void nonPositiveDocumentLimit_throwsIllegalArgument() {
        assertThatThrownBy(() -> new DefaultChunkingService(RULE, COUNTER, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultChunkingService(RULE, COUNTER, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("zero minimum characters with a positive document limit is legal")
    void zeroMinimumCharacters_isLegal() {
        assertThatCode(() -> new DefaultChunkingService(RULE, COUNTER, 0, 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null rule and token counter are rejected")
    void nullParameters_throwNullPointer() {
        assertThatThrownBy(() -> new DefaultChunkingService(null, COUNTER, 0, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultChunkingService(RULE, null, 0, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the default constructor creates a usable service")
    void noArgConstructor_isUsable() {
        assertThatCode(() -> new DefaultChunkingService().chunk("content"))
                .doesNotThrowAnyException();
    }
}
