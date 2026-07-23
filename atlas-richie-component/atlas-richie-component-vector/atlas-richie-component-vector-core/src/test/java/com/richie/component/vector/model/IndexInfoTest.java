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
package com.richie.component.vector.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndexInfoTest {

    @Test
    void record_carriesAllFieldValues() {
        Instant now = Instant.now();
        Map<String, Object> meta = Map.of("replicas", 2, "shards", 3);

        IndexInfo info = new IndexInfo(
                "docs",
                Modality.TEXT,
                1536,
                "cosine",
                "hnsw",
                IndexStatus.READY,
                42L,
                now,
                now,
                meta);

        assertThat(info.name()).isEqualTo("docs");
        assertThat(info.modality()).isEqualTo(Modality.TEXT);
        assertThat(info.dimension()).isEqualTo(1536);
        assertThat(info.metric()).isEqualTo("cosine");
        assertThat(info.indexType()).isEqualTo("hnsw");
        assertThat(info.status()).isEqualTo(IndexStatus.READY);
        assertThat(info.documentCount()).isEqualTo(42L);
        assertThat(info.createdAt()).isEqualTo(now);
        assertThat(info.updatedAt()).isEqualTo(now);
        assertThat(info.metadata()).containsEntry("replicas", 2).containsEntry("shards", 3);
    }

    @Test
    void record_allowsNullOptionalFields() {
        IndexInfo info = new IndexInfo(
                "products", Modality.IMAGE, 1024, "cosine", "hnsw",
                IndexStatus.CREATING, null, null, null, null);

        assertThat(info.documentCount()).isNull();
        assertThat(info.createdAt()).isNull();
        assertThat(info.updatedAt()).isNull();
        assertThat(info.metadata()).isNull();
    }
}
