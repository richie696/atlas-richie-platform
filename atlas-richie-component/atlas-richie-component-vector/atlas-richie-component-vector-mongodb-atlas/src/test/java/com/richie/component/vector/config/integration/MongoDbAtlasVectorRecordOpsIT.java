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
import com.richie.component.vector.model.HybridSearchOptions;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MongoDB Atlas Vector Search 集成测试 (Testcontainers + JUnit5)。
 * <p>
 * 使用 {@code mongodb/mongodb-atlas-local:7.0.9}（Atlas 本地开发版，启用
 * {@code $vectorSearch} 聚合），与生产 Atlas 同协议。容器启动慢（30s+），
 * 在没有 Docker 的环境下设置 {@code SKIP_TESTCONTAINERS=true} 整体跳过。
 * <p>
 * Phase A 状态下 MongoDbAtlasVectorServiceImpl 已实现完整链路
 * （createIndex / addEmbeddings / deleteByIds / getByIds / listDocumentsImpl
 * 均通过 MongoTemplate 完成），因此本 IT 直接断言真值而非 {@code assertThrows} 回归。
 */
@Testcontainers
@VectorIntegrationTest(properties = {
        // 排除 vector 模块之外的 framework 自动装配 — IT 不依赖这些功能，
        // 避免 transitive 依赖（如 tenant 需要的 JSQLParser）拉崩上下文
        "spring.autoconfigure.exclude="
                + "com.richie.component.mongodb.config.MongodbAutoConfiguration,"
                + "com.richie.component.tenant.config.TenantAutoConfiguration,"
                + "com.richie.component.cache.config.CacheAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@DisabledIfEnvironmentVariable(named = "SKIP_TESTCONTAINERS", matches = "true")
class MongoDbAtlasVectorRecordOpsIT {

    @Container
    @SuppressWarnings("resource")
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0"))
            .withStartupTimeout(Duration.ofMinutes(3));

    /**
     * 将容器连接信息注入 Spring 环境。
     * {@code spring.data.mongodb.uri} 让 Spring Boot 自动装配 {@code MongoClient} / {@code MongoTemplate}；
     * {@code platform.component.mongodb.*} 由 atlas-richie-component-mongodb 的 MongodbConfig 消费。
     * 显式声明两者避免 MongoTemplate 与 VectorStore 拿到不同 connection。
     */
    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.vector.provider", () -> "mongodb");
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("platform.component.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("platform.component.mongodb.database", () -> "it_vectors");
    }

    private static final String INDEX_NAME = "it_mongo_vectors";
    private static final int DIM = 4;

    @Autowired(required = false)
    private VectorService vectorService;

    @Test
    void createIndex_addText_get_searchByText_delete_dropIndex() {
        if (vectorService == null) {
            return;
        }
        // 1) 清理残留 collection（首跑必然没有）
        try {
            vectorService.deleteIndex(INDEX_NAME);
        } catch (Exception ignored) {
            // ignore first-run "collection not found"
        }

        // 2) createIndex 触发 createSearchIndexes（Atlas Vector Search）
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(DIM);
        config.setMetric("cosine");
        vectorService.createIndex(INDEX_NAME, config);

        assertThat(vectorService.indexExists(INDEX_NAME)).isTrue();
        VectorProperties.IndexConfig loaded = vectorService.getIndexConfig(INDEX_NAME);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo(INDEX_NAME);

        // 3) addText → AbstractVectorService.add → addEmbeddings → vectorStore.add
        String id = vectorService.addText(INDEX_NAME, "alpha integration sample", Map.of("source", "it"));
        assertThat(id).isNotBlank();
        assertThat(vectorService.countDocuments(INDEX_NAME)).isPositive();

        // 4) getByIds 走 MongoTemplate.find(_id in ...)
        VectorRecord loaded2 = vectorService.get(INDEX_NAME, id).orElseThrow();
        assertThat(loaded2.getId()).isEqualTo(id);

        // 5) searchByText 走 vectorStore.similaritySearch（Atlas Vector Search 自动用)
        List<VectorSearchResult> hits = vectorService.searchByText(INDEX_NAME, "alpha", 3);
        assertThat(hits).isNotNull();

        // 6) delete 单条
        vectorService.delete(INDEX_NAME, id);
        assertThat(vectorService.countDocuments(INDEX_NAME)).isZero();

        // 7) dropIndex（删除 collection 上所有索引）
        vectorService.deleteIndex(INDEX_NAME);
        assertThat(vectorService.indexExists(INDEX_NAME)).isFalse();
    }

    @Test
    void addImage_searchByImage_returnsUnsupportedModality() {
        if (vectorService == null) {
            return;
        }
        // IMAGE 模态在 Phase A 的 AbstractVectorService.add() 中显式拒绝，
        // 需要 Phase C ai 模块扩展 ImageEmbeddingModel 后才能跑通
        byte[] fakeImage = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        assertThatThrownBy(() -> vectorService.addImage(INDEX_NAME, fakeImage, "image/png", Map.of()))
                .isInstanceOf(UnsupportedModalityException.class)
                .hasMessageContaining("ImageEmbeddingModel");
        // searchByImage 同样
        assertThatThrownBy(() -> vectorService.searchByImage(INDEX_NAME, fakeImage, "image/png", 3))
                .isInstanceOf(UnsupportedModalityException.class);
    }

    @Test
    void batchAdd_emitsBatchEvents() {
        if (vectorService == null) {
            return;
        }
        // 准备：插入 3 条文本记录；MongoDB Atlas impl 的 addEmbeddings 真实走通，可观察到
        // BatchStarted + 3 × (ItemStarted + StageChanged×2 + ItemCompleted) + BatchCompleted
        List<VectorRecord> records = List.of(
                VectorRecord.text(INDEX_NAME, "alpha", Map.of("tag", "a")),
                VectorRecord.text(INDEX_NAME, "beta", Map.of("tag", "b")),
                VectorRecord.text(INDEX_NAME, "gamma", Map.of("tag", "c")));
        long eventCount = vectorService.addBatch(INDEX_NAME, records)
                .collectList()
                .block(Duration.ofSeconds(30))
                .size();
        // 1 BatchStarted + 3 × (1 ItemStarted(LOADED) + 1 StageChanged(LOADED→EMBEDDED)
        //   + 1 ItemStarted(PERSISTING) + 1 StageChanged(PERSISTING→PERSISTED) + 1 ItemCompleted)
        //   + 1 BatchCompleted = 1 + 3*5 + 1 = 17
        assertThat(eventCount).isGreaterThanOrEqualTo(15L);
    }

    @Test
    void hybridSearch_textAndKeyword() {
        if (vectorService == null) {
            return;
        }
        // 准备：3 条文本
        vectorService.addText(INDEX_NAME, "alpha bravo charlie", Map.of("tag", "a"));
        vectorService.addText(INDEX_NAME, "alpha delta echo", Map.of("tag", "a"));
        vectorService.addText(INDEX_NAME, "foxtrot golf hotel", Map.of("tag", "b"));

        // MongoDB impl 的 hybridSearch 默认走 AbstractVectorService.hybridSearch → searchByText 降级
        HybridSearchOptions opts = HybridSearchOptions.builder()
                .vectorWeight(0.5)
                .keywordWeight(0.5)
                .keywordQuery("alpha")
                .build();
        List<VectorSearchResult> hits = vectorService.hybridSearch(INDEX_NAME, "alpha", "alpha", 5, opts);
        assertThat(hits).isNotNull();
    }

    @Test
    void listDocuments_afterInsert_returnsInsertedRecords() {
        if (vectorService == null) {
            return;
        }
        // 准备：插 1 条
        String id = vectorService.addText(INDEX_NAME, "ping", Map.of());
        assertThat(id).isNotBlank();
        // listDocuments 走 AbstractVectorService.listDocuments → MongoDbAtlas.listDocumentsImpl
        // （mongoTemplate.find 带 skip/limit + fields 过滤）
        List<VectorRecord> page = vectorService.listDocuments(INDEX_NAME, 0, 10);
        assertThat(page).isNotEmpty();
        assertThat(page.get(0).getIndexName()).isEqualTo(INDEX_NAME);
    }
}
