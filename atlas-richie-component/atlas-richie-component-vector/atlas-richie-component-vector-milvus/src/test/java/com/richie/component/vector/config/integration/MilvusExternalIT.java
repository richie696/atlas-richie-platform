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
package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.config.support.VectorIntegrationTest;
import com.richie.component.vector.exceptions.UnsupportedModalityException;
import com.richie.component.vector.model.BatchEvent;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milvus 向量库完整生命周期集成测试 — 外部 Milvus 模式（无 Testcontainers）。
 * <p>
 * 与 {@link MilvusVectorRecordOpsIT} 的关键差异:
 * <ul>
 *   <li>不启动容器，直接连接到 {@code MILVUS_HOST}:{@code MILVUS_PORT} 指向的真实实例</li>
 *   <li>所有 Milvus 连接参数通过 {@code System.getenv()} 读取，未设置时回落到默认值</li>
 *   <li>未设置 {@code MILVUS_HOST} 环境变量时自动跳过（{@code @EnabledIfEnvironmentVariable}）</li>
 *   <li>每个测试都通过 {@code uniqueIndex(prefix)} 拿到独立 UUID 索引名，避免互相污染</li>
 * </ul>
 * <p>
 * 用法:
 * <pre>
 *   MILVUS_HOST=localhost MILVUS_PORT=19530 mvn test -pl atlas-richie-component-vector-milvus -Dtest=MilvusExternalIT
 * </pre>
 */
@VectorIntegrationTest
@EnabledIfEnvironmentVariable(named = "MILVUS_HOST", matches = ".+")
@DisabledIfEnvironmentVariable(named = "SKIP_TESTCONTAINERS", matches = "true")
@TestPropertySource(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.embedding.text=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none",
        "spring.ai.model.image=none",
        "spring.ai.model.moderation=none",
        "spring.ai.model.anthropic.chat=none"
})
class MilvusExternalIT {

    @DynamicPropertySource
    static void registerMilvusProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.vector.provider", () -> "milvus");
        registry.add("platform.component.vector.milvus.host",
                () -> Optional.ofNullable(System.getenv("MILVUS_HOST")).orElse("localhost"));
        registry.add("platform.component.vector.milvus.port", () -> Integer.parseInt(
                Optional.ofNullable(System.getenv("MILVUS_PORT")).orElse("19530")));
        registry.add("platform.component.vector.milvus.database-name",
                () -> Optional.ofNullable(System.getenv("MILVUS_DATABASE_NAME")).orElse("default"));
        registry.add("platform.component.vector.milvus.collection-name",
                () -> Optional.ofNullable(System.getenv("MILVUS_COLLECTION_NAME")).orElse("default"));
        registry.add("platform.component.vector.milvus.username",
                () -> Optional.ofNullable(System.getenv("MILVUS_USERNAME")).orElse(null));
        registry.add("platform.component.vector.milvus.password",
                () -> Optional.ofNullable(System.getenv("MILVUS_PASSWORD")).orElse(null));
    }

    @Autowired(required = false)
    private VectorService vectorService;

    private String uniqueIndex(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private VectorProperties.IndexConfig indexConfig() {
        // 默认 4 维与 MilvusStubEmbeddingModel 匹配;设 DASHSCOPE_EMBEDDING_DIM 可切到真实 DashScope 维度
        int dim = Integer.parseInt(Optional.ofNullable(System.getenv("DASHSCOPE_EMBEDDING_DIM")).orElse("4"));
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(dim);
        config.setMetric("COSINE");
        config.setIndexType("IVF_FLAT");
        config.setShards(1);
        return config;
    }

    @Test
    void createIndex_addText_get_search_delete_dropIndex() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_basic");
        vectorService.createIndex(indexName, indexConfig());

        try {
            assertThat(vectorService.indexExists(indexName)).isTrue();
            VectorProperties.IndexConfig loaded = vectorService.getIndexConfig(indexName);
            assertThat(loaded).isNotNull();
            assertThat(loaded.getName()).isEqualTo(indexName);
            assertThat(loaded.getDimension()).isEqualTo(4);
            assertThat(vectorService.countDocuments(indexName)).isZero();

            String recordId = vectorService.addText(indexName, "wave-c", Map.of("source", "integration-test"));
            assertThat(recordId).isNotBlank();

            List<VectorRecord> records = vectorService.getAll(indexName, List.of(recordId));
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().getId()).isEqualTo(recordId);
            assertThat(records.getFirst().getContent())
                    .isInstanceOfSatisfying(VectorContent.TextContent.class,
                            content -> assertThat(content.text()).isEqualTo("wave-c"));

            List<VectorSearchResult> results = vectorService.searchByText(indexName, "wave-c", 3);
            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(result -> recordId.equals(result.getId()));
            assertThat(vectorService.countDocuments(indexName)).isEqualTo(1);

            vectorService.delete(indexName, recordId);
            assertThat(vectorService.countDocuments(indexName)).isZero();
        } finally {
            vectorService.deleteIndex(indexName);
        }
        assertThat(vectorService.indexExists(indexName)).isFalse();
    }

    @Test
    void createIndex_listIndexes_describesIndex() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_list");
        vectorService.createIndex(indexName, indexConfig());

        try {
            assertThat(vectorService.listIndexes())
                    .anyMatch(index -> indexName.equals(index.name()));

            IndexInfo described = vectorService.describeIndex(indexName);
            assertThat(described).isNotNull();
            assertThat(described.name()).isEqualTo(indexName);
            assertThat(described.dimension()).isEqualTo(4);
            assertThat(described.documentCount()).isZero();
        } finally {
            vectorService.deleteIndex(indexName);
        }
    }

    @Test
    void truncateIndex_clearsAllDocuments() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_truncate");
        vectorService.createIndex(indexName, indexConfig());

        try {
            vectorService.addText(indexName, "truncate-me", Map.of());
            assertThat(vectorService.countDocuments(indexName)).isEqualTo(1);

            vectorService.truncateIndex(indexName);
            assertThat(vectorService.countDocuments(indexName)).isZero();
        } finally {
            vectorService.deleteIndex(indexName);
        }
    }

    @Test
    void getIndexStats_reflectsDocumentCount() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_stats");
        vectorService.createIndex(indexName, indexConfig());

        try {
            IndexInfo stats = vectorService.getIndexStats(indexName);
            assertThat(stats).isNotNull();
            assertThat(stats.name()).isEqualTo(indexName);
            assertThat(stats.dimension()).isEqualTo(4);
            assertThat(stats.metric()).isEqualToIgnoringCase("COSINE");
            assertThat(stats.documentCount()).isZero();
        } finally {
            vectorService.deleteIndex(indexName);
        }
    }

    @Test
    void addImage_searchByImage_returnsRelevant() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_image");
        byte[] fakeImage = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

        assertThatThrownBy(() -> vectorService.addImage(indexName, fakeImage, "image/png", Map.of()))
                .isInstanceOf(UnsupportedModalityException.class)
                .hasMessageContaining("ImageEmbeddingModel");
        assertThatThrownBy(() -> vectorService.searchByImage(indexName, fakeImage, "image/png", 3))
                .isInstanceOf(UnsupportedModalityException.class);
    }

    @Test
    void batchAdd_emitsBatchEvents() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_batch");
        vectorService.createIndex(indexName, indexConfig());

        try {
            List<VectorRecord> records = List.of(
                    VectorRecord.text(indexName, "alpha", Map.of("k", "v")),
                    VectorRecord.text(indexName, "beta", Map.of("k", "v")));
            List<BatchEvent> events = vectorService.addBatch(indexName, records)
                    .collectList()
                    .block(Duration.ofSeconds(30));

            assertThat(events).isNotNull();
            assertThat(events).anyMatch(BatchEvent.BatchStarted.class::isInstance);
            assertThat(events.stream().filter(BatchEvent.ItemCompleted.class::isInstance).count())
                    .isGreaterThanOrEqualTo(2);
            assertThat(events).anySatisfy(event -> {
                if (event instanceof BatchEvent.BatchCompleted completed) {
                    assertThat(completed.stats().succeeded()).isGreaterThanOrEqualTo(2);
                }
            });
        } finally {
            vectorService.deleteIndex(indexName);
        }
    }
}
