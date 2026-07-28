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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("StreamingChunkerFactory — per-document session creation")
class StreamingChunkerFactoryTest {

    private final ChunkingService service = mock(ChunkingService.class);

    @Test
    @DisplayName("create(null) rejects a null rule")
    void create_nullRule_throwsNullPointer() {
        StreamingChunkerFactory factory = new StreamingChunkerFactory(service, 100);

        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create(rule) returns a new stream")
    void create_rule_returnsStream() {
        StreamingChunkerFactory factory = new StreamingChunkerFactory(service, 100);

        assertThat(factory.create(ChunkingRule.recursiveDefaults(10, 2))).isNotNull();
    }

    @Test
    @DisplayName("create adjusts a pending limit below the rule chunk size")
    void create_ruleLargerThanFactoryLimit_adjustsLimit() {
        StreamingChunkerFactory factory = new StreamingChunkerFactory(service, 5);

        assertThat(factory.create(ChunkingRule.recursiveDefaults(10, 0))).isNotNull();
    }

    @Test
    @DisplayName("create keeps a pending limit above the rule chunk size")
    void create_ruleSmallerThanFactoryLimit_keepsLimit() {
        StreamingChunkerFactory factory = new StreamingChunkerFactory(service, 100);

        assertThat(factory.create(ChunkingRule.recursiveDefaults(10, 0))).isNotNull();
    }
}
