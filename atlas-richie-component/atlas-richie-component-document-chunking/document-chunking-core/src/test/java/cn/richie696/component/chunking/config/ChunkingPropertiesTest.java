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
package cn.richie696.component.chunking.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChunkingProperties — defaults, chained setters, nested types")
class ChunkingPropertiesTest {

    @Test
    @DisplayName("documented defaults are loaded when no properties are bound")
    void chunkingProperties_defaults_matchDesign() {
        ChunkingProperties properties = new ChunkingProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultRule()).isEqualTo("recursive");
        assertThat(properties.getMaxCharacters()).isEqualTo(1600);
        assertThat(properties.getOverlapCharacters()).isEqualTo(160);
        assertThat(properties.getMinChunkCharacters()).isEqualTo(80);
        assertThat(properties.getMaxChunksPerDocument()).isEqualTo(10_000);
        assertThat(properties.getStreaming()).isNotNull();
        assertThat(properties.getStreaming().getMaxPendingCharacters()).isEqualTo(8192);
        assertThat(properties.getRecursive()).isNotNull();
        assertThat(properties.getRecursive().getSeparators()).containsExactly(
                "\n\n", "\n", "。", "！", "？", ". ", " ");
    }

    @Test
    @DisplayName("Lombok @Accessors(chain=true) setters return the same instance")
    void chunkingProperties_chainSetters_returnSameInstance() {
        ChunkingProperties properties = new ChunkingProperties();

        ChunkingProperties returned = properties
                .setEnabled(false)
                .setDefaultRule("fixed")
                .setMaxCharacters(2048)
                .setOverlapCharacters(128)
                .setMinChunkCharacters(40)
                .setMaxChunksPerDocument(500);

        assertThat(returned).isSameAs(properties);
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDefaultRule()).isEqualTo("fixed");
        assertThat(properties.getMaxCharacters()).isEqualTo(2048);
        assertThat(properties.getOverlapCharacters()).isEqualTo(128);
        assertThat(properties.getMinChunkCharacters()).isEqualTo(40);
        assertThat(properties.getMaxChunksPerDocument()).isEqualTo(500);
    }

    @Test
    @DisplayName("nested Streaming sub-properties support chained setters")
    void chunkingProperties_streamingSubProperties_supportChainedSetters() {
        ChunkingProperties properties = new ChunkingProperties();

        ChunkingProperties.Streaming returned = properties.getStreaming().setMaxPendingCharacters(16);

        assertThat(returned).isSameAs(properties.getStreaming());
        assertThat(properties.getStreaming().getMaxPendingCharacters()).isEqualTo(16);
    }

    @Test
    @DisplayName("nested Recursive sub-properties support chained setters")
    void chunkingProperties_recursiveSubProperties_supportChainedSetters() {
        ChunkingProperties properties = new ChunkingProperties();

        ChunkingProperties.Recursive returned = properties.getRecursive()
                .setSeparators(java.util.List.of("\n\n", "\n"));

        assertThat(returned).isSameAs(properties.getRecursive());
        assertThat(properties.getRecursive().getSeparators()).containsExactly("\n\n", "\n");
    }

    @Test
    @DisplayName("nested types are non-null by default and can be swapped via top-level setter")
    void chunkingProperties_nestedTypes_canBeReplaced() {
        ChunkingProperties properties = new ChunkingProperties();
        ChunkingProperties.Streaming newStreaming = new ChunkingProperties.Streaming().setMaxPendingCharacters(8);
        ChunkingProperties.Recursive newRecursive = new ChunkingProperties.Recursive()
                .setSeparators(java.util.List.of("===", "---"));

        properties.setStreaming(newStreaming);
        properties.setRecursive(newRecursive);

        assertThat(properties.getStreaming()).isSameAs(newStreaming);
        assertThat(properties.getRecursive()).isSameAs(newRecursive);
        assertThat(properties.getStreaming().getMaxPendingCharacters()).isEqualTo(8);
        assertThat(properties.getRecursive().getSeparators()).containsExactly("===", "---");
    }
}
