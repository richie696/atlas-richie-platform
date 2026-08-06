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

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChunkingService — default chunk delegation")
class ChunkingServiceDefaultMethodTest {

    @Test
    @DisplayName("chunk(content) delegates to recursiveDefaults with platform defaults")
    void chunk_withoutRule_delegatesToRecursiveDefaults() {
        AtomicReference<ChunkingRule> receivedRule = new AtomicReference<>();
        ChunkingService service = new ChunkingService() {
            @Override
            public ChunkingResult chunk(String content, ChunkingRule rule) {
                receivedRule.set(rule);
                return new ChunkingResult(List.of(new Chunk(0, content, 0, content.length())));
            }
        };

        assertThat(service.chunk("content").chunks()).hasSize(1);
        assertThat(receivedRule.get())
                .isEqualTo(ChunkingRule.recursiveDefaults(1_600, 160));
    }

    @Test
    @DisplayName("recursiveDefaults carries the default rule identity and parameters")
    void recursiveDefaults_hasExpectedDefaults() {
        ChunkingRule rule = ChunkingRule.recursiveDefaults(1_600, 160);

        assertThat(rule.ruleId()).isEqualTo("default-recursive");
        assertThat(rule.version()).isEqualTo("1");
        assertThat(rule.strategy()).isEqualTo(ChunkingRule.Strategy.RECURSIVE);
        assertThat(rule.maxCharacters()).isEqualTo(1_600);
        assertThat(rule.overlapCharacters()).isEqualTo(160);
        assertThat(rule.separators()).isNotEmpty();
    }
}
