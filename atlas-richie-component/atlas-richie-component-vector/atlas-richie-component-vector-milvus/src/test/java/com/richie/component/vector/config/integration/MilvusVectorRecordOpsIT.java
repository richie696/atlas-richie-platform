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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milvus 向量库完整生命周期集成测试（Testcontainers + JUnit 5）。
 * 使用真实 Milvus 容器验证文本 CRUD、索引管理、统计与批量事件。
 */
@Testcontainers
@VectorIntegrationTest
@DisabledIfEnvironmentVariable(named = "SKIP_TESTCONTAINERS", matches = "true")
class MilvusVectorRecordOpsIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> MILVUS = new GenericContainer<>(DockerImageName.parse("milvusdb/milvus:v2.4-latest"))
            .withExposedPorts(19530)
            .withStartupTimeout(Duration.ofMinutes(6));

    @DynamicPropertySource
    static void registerMilvusProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.vector.provider", () -> "milvus");
        registry.add("platform.component.vector.milvus.host", MILVUS::getHost);
        registry.add("platform.component.vector.milvus.port", MILVUS::getFirstMappedPort);
        registry.add("platform.component.vector.milvus.database-name", () -> "default");
        registry.add("platform.component.vector.milvus.collection-name", () -> "it_milvus_vectors");
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
        String indexName = uniqueIndex("it_milvus_basic");
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
        String indexName = uniqueIndex("it_milvus_list");
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
        String indexName = uniqueIndex("it_milvus_truncate");
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
        String indexName = uniqueIndex("it_milvus_stats");
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
        String indexName = uniqueIndex("it_milvus_image");
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
        String indexName = uniqueIndex("it_milvus_batch");
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
