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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorRecordTest {

    @Test
    void builder_createsCompleteRecord() {
        VectorRecord record = new VectorRecord()
                .setId("rec-1")
                .setIndexName("docs")
                .setContent(new VectorContent.TextContent("hello", "text/plain"))
                .setTags(new String[]{"a", "b"})
                .setSource("kb")
                .setStatus("active")
                .setScore(0.95);

        assertThat(record.getId()).isEqualTo("rec-1");
        assertThat(record.getIndexName()).isEqualTo("docs");
        assertThat(record.getContent().modality()).isEqualTo(Modality.TEXT);
        assertThat(record.getTags()).containsExactly("a", "b");
        assertThat(record.getScore()).isEqualTo(0.95);
    }

    @Test
    void textFactory_setsDefaultsAndUuid() {
        VectorRecord record = VectorRecord.text("docs", "hello");

        assertThat(record.getId()).isNotBlank();
        assertThat(record.getIndexName()).isEqualTo("docs");
        assertThat(record.getContent()).isInstanceOf(VectorContent.TextContent.class);
        assertThat(record.getStatus()).isEqualTo("active");
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void imageFactory_wrapsBytesAndMime() {
        VectorRecord record = VectorRecord.image("imgs", new byte[]{1, 2, 3}, "image/png");

        assertThat(record.getIndexName()).isEqualTo("imgs");
        VectorContent.ImageContent image = (VectorContent.ImageContent) record.getContent();
        assertThat(image.data()).containsExactly(1, 2, 3);
        assertThat(image.mimeType()).isEqualTo("image/png");
    }

    @Test
    void itemId_prefersMetaOverId() {
        Map<String, Object> meta = new HashMap<>();
        meta.put(VectorRecord.META_ITEM_ID, "batch-7");
        VectorRecord record = new VectorRecord().setId("rec-99").setMetadata(meta);

        assertThat(record.itemId()).isEqualTo("batch-7");
    }

    @Test
    void itemId_fallsBackToIdWhenMetaMissing() {
        VectorRecord record = new VectorRecord().setId("rec-99");

        assertThat(record.itemId()).isEqualTo("rec-99");
    }
}