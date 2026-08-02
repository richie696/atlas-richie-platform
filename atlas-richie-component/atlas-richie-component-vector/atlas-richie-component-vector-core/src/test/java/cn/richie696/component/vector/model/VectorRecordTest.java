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
package cn.richie696.component.vector.model;

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
    void textFactory_setsDefaults() {
        VectorRecord record = VectorRecord.text("docs", "hello");

        assertThat(record.getId()).isNotNull();
        assertThat(record.getIndexName()).isEqualTo("docs");
        assertThat(record.getContent()).isInstanceOf(VectorContent.TextContent.class);
        assertThat(record.getStatus()).isEqualTo("ACTIVE");
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void imageFactory_wrapsBytesAndMimeType() {
        VectorRecord record = VectorRecord.image("images", new byte[]{1, 2, 3}, "image/png");

        assertThat(record.getContent()).isInstanceOf(VectorContent.ImageContent.class);
        assertThat(record.getContent().modality()).isEqualTo(Modality.IMAGE);
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
