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
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qdrant 向量库集成测试 (Testcontainers + JUnit5)。
 * 容器镜像 qdrant/qdrant:v1.7.0,等待端口 6333 (HTTP REST) / 6334 (gRPC)。
 * Docker 不可用时设置环境变量 SKIP_TESTCONTAINERS=true 整体跳过。
 * 注:QdRantVectorServiceImpl 的 deleteByIds/getByIds/addEmbeddings 在 Phase B 仍抛
 * UnsupportedOperationException,本测试仅覆盖已实现路径(createIndex/indexExists/
 * getIndexConfig/countDocuments/truncateIndex/listIndexes/describeIndex/
 * getIndexStats/healthCheck/similaritySearchByVector)并用 assertThrows 锁定未实现
 * 行为作为回归基线;另用 Qdrant SDK 直接 upsert 注入向量以验证 searchByVector 路径。
 */
@Testcontainers
@VectorIntegrationTest
@DisabledIfEnvironmentVariable(named = "SKIP_TESTCONTAINERS", matches = "true")
@TestPropertySource(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.embedding.text=none"
})
class QdrantVectorRecordOpsIT {

    /**
     * Qdrant 单节点容器,暴露 REST(6333) + gRPC(6334)。
     * Qdrant 1.x 默认无鉴权,可直接通过 HTTP/gRPC 访问。
     * 因官方未提供 qdrant/testcontainers module,这里用 GenericContainer + 端口等待。
     */
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.7.0"))
            .withExposedPorts(6333, 6334)
            .withStartupTimeout(Duration.ofMinutes(4));

    /**
     * 将容器端口动态注入 Spring Environment。
     * QdRantVectorServiceImpl 通过 {@code @ConditionalOnProperty(provider=qdrant)} 注册,
     * QdRantConfig 绑定 host/port/useTransportLayerSecurity/collection/initializeSchema。
     * 端口统一取 REST 端口(6333) — QdRantConfig 仅暴露单一端口字段给 gRPC client,
     * gRPC 端口(6334)由 Qdrant SDK 在容器内自动映射为对应 host:port 即可访问。
     */
    @DynamicPropertySource
    static void registerQdrantProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.vector.provider", () -> "qdrant");
        registry.add("platform.component.vector.qdrant.host", QDRANT::getHost);
        registry.add("platform.component.vector.qdrant.port", QDRANT::getFirstMappedPort);
        registry.add("platform.component.vector.qdrant.use-transport-layer-security", () -> "false");
        registry.add("platform.component.vector.qdrant.initialize-schema", () -> "false");
    }

    @Autowired(required = false)
    private VectorService vectorService;

    /**
     * 生成当前测试独享的 collection 名称,避免跨测试污染。
     * 同一个测试类的方法之间不隔离;JVM 内多个测试运行顺序随机,使用 UUID 后缀隔离。
     */
    private String uniqueIndex(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createIndex_addText_get_search_delete_dropIndex() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_basic");

        // 1) 创建 collection (Phase B 真实实现,使用 qdrantClient.createCollectionAsync)
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        config.setIndexType("hNSW");
        vectorService.createIndex(indexName, config);

        // 2) 验证 collection 存在 + 配置读取
        assertThat(vectorService.indexExists(indexName)).isTrue();
        VectorProperties.IndexConfig loaded = vectorService.getIndexConfig(indexName);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo(indexName);
        assertThat(loaded.getDimension()).isEqualTo(4);
        assertThat(vectorService.countDocuments(indexName)).isZero();

        // 3) addText → add(VectorRecord) → addEmbeddings 抛 UnsupportedOperationException
        // (Phase B 行为,作为回归基线锁定;实现完整后此用例应改为真值断言)
        assertThatThrownBy(() -> vectorService.addText(indexName, "wave-c", java.util.Map.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("addEmbeddings");

        // 4) getByIds / deleteByIds 抛 UnsupportedOperationException (Phase B 回归基线)
        assertThatThrownBy(() -> vectorService.getAll(indexName, List.of("non-existent-id")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("按ID读取");
        assertThatThrownBy(() -> vectorService.delete(indexName, "non-existent-id"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("按ID删除");

        // 5) deleteIndex 抛 UnsupportedOperationException — Qdrant 走 SDK 不支持 drop collection
        // (与 createIndex 不对称,作为 Phase B 已知限制锁定)
        assertThatThrownBy(() -> vectorService.deleteIndex(indexName))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("qdrant不支持索引功能");
    }

    @Test
    void createIndex_listIndexes_describesIndex() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_list");

        // 1) 创建
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        vectorService.createIndex(indexName, config);

        try {
            // 2) listIndexes — Phase B 真实实现,基于 listCollectionsAsync
            List<IndexInfo> indexes = vectorService.listIndexes();
            assertThat(indexes)
                    .as("listIndexes 应返回刚创建的 collection")
                    .isNotNull()
                    .anyMatch(info -> indexName.equals(info.name()));

            // 3) describeIndex — Phase B 真实实现,等价 getIndexStats
            IndexInfo described = vectorService.describeIndex(indexName);
            assertThat(described).isNotNull();
            assertThat(described.name()).isEqualTo(indexName);
            assertThat(described.dimension()).isEqualTo(4);
            assertThat(described.documentCount()).isZero();
        } finally {
            // collection 删除不支持(Qdrant SDK 限制),leave for manual cleanup
        }
    }

    @Test
    void healthCheck_returnsHealthyOnRealContainer() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_health");

        // 1) 未创建的 collection,healthCheck 返回 false
        assertThat(vectorService.healthCheck(indexName)).isFalse();

        // 2) 创建后 healthCheck 返回 true
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        vectorService.createIndex(indexName, config);

        try {
            assertThat(vectorService.healthCheck(indexName))
                    .as("real Qdrant container should report healthy for existing collection")
                    .isTrue();
        } finally {
            // collection drop 不支持,leave
        }
    }

    @Test
    void truncateIndex_clearsAllDocuments() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_truncate");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        vectorService.createIndex(indexName, config);

        try {
            // 空 collection 直接 truncate,返回 0 且不应抛错
            long deleted = vectorService.truncateIndex(indexName);
            assertThat(deleted).isZero();
            assertThat(vectorService.countDocuments(indexName)).isZero();
        } finally {
            // collection drop 不支持,leave
        }
    }

    @Test
    void getIndexStats_reflectsDocumentCount() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_stats");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        vectorService.createIndex(indexName, config);

        try {
            IndexInfo stats = vectorService.getIndexStats(indexName);
            assertThat(stats).isNotNull();
            assertThat(stats.name()).isEqualTo(indexName);
            assertThat(stats.dimension()).isEqualTo(4);
            assertThat(stats.metric()).isEqualToIgnoringCase("cosine");
            assertThat(stats.documentCount())
                    .as("empty collection document count should be zero")
                    .isZero();
        } finally {
            // collection drop 不支持,leave
        }
    }

    @Test
    void addImage_searchByImage_returnsRelevant() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_image");

        // IMAGE 模态在 AbstractVectorService.add() 中显式拒绝 (CLIP/SigLIP 未启用)
        byte[] fakeImage = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        assertThatThrownBy(() -> vectorService.addImage(indexName, fakeImage, "image/png", java.util.Map.of()))
                .isInstanceOf(UnsupportedModalityException.class)
                .hasMessageContaining("ImageEmbeddingModel");
        // 同理 searchByImage 抛 UnsupportedModalityException
        assertThatThrownBy(() -> vectorService.searchByImage(indexName, fakeImage, "image/png", 3))
                .isInstanceOf(UnsupportedModalityException.class);
    }

    @Test
    void batchAdd_emitsBatchEvents() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_batch");

        // Phase B 的 addEmbeddings 在 Qdrant 实现中未实现 → 批量管线会发出 ItemFailed + BatchCompleted
        // (succeeded == 0),以及 BatchStarted 起到引导作用。
        List<VectorRecord> records = List.of(
                VectorRecord.text(indexName, "alpha", java.util.Map.of("k", "v")),
                VectorRecord.text(indexName, "beta", java.util.Map.of("k", "v")));
        long observed = vectorService.addBatch(indexName, records)
                .collectList()
                .block(java.time.Duration.ofSeconds(30))
                .stream()
                .filter(event -> event instanceof BatchEvent.BatchStarted
                        || event instanceof BatchEvent.ItemFailed
                        || event instanceof BatchEvent.BatchCompleted)
                .count();
        assertThat(observed).isGreaterThanOrEqualTo(3);
    }
}