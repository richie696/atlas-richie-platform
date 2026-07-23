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
import com.richie.component.vector.model.IndexInfo;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qdrant 单用途集成测试 — 仅覆盖 Phase B 已实现的 healthCheck + getIndexStats + listIndexes + describeIndex。
 * <p>
 * 与 {@link QdrantVectorRecordOpsIT} 的区别:本 IT 不验证 CRUD/搜索路径,只验证运维探针类方法,
 * 用于 Phase B+ 交付确认 — 这些方法不需要写入数据,可作为快速冒烟测试在 CI 早期运行。
 */
@Testcontainers
@VectorIntegrationTest
@DisabledIfEnvironmentVariable(named = "SKIP_TESTCONTAINERS", matches = "true")
class QdrantHealthAndStatsIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.7.0"))
            .withExposedPorts(6333, 6334)
            .withStartupTimeout(Duration.ofMinutes(4));

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

    private String uniqueIndex(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void healthCheck_onMissingCollection_returnsFalse() {
        if (vectorService == null) {
            return;
        }
        assertThat(vectorService.healthCheck("it_never_existed_" + UUID.randomUUID()))
                .as("healthCheck on missing collection should be false")
                .isFalse();
    }

    @Test
    void healthCheck_and_getIndexStats_onFreshCollection_areConsistent() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_hs_consistent");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        vectorService.createIndex(indexName, config);

        try {
            // healthCheck 与 getIndexStats 协同 — 同一个 collection 在 3 秒探针窗口内应同时报告健康/统计
            assertThat(vectorService.healthCheck(indexName)).isTrue();

            IndexInfo stats = vectorService.getIndexStats(indexName);
            assertThat(stats).isNotNull();
            assertThat(stats.name()).isEqualTo(indexName);
            assertThat(stats.dimension()).isEqualTo(4);
            assertThat(stats.metric()).isEqualToIgnoringCase("cosine");
            assertThat(stats.documentCount()).isZero();
        } finally {
            // collection drop 不支持,leave
        }
    }

    @Test
    void listIndexes_afterCreate_containsCreated() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_hs_list");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("cosine");
        vectorService.createIndex(indexName, config);

        try {
            List<IndexInfo> indexes = vectorService.listIndexes();
            assertThat(indexes)
                    .as("listIndexes 应返回含当前测试创建 collection 的非空列表")
                    .isNotNull()
                    .anyMatch(info -> indexName.equals(info.name())
                            && info.dimension() != null
                            && info.dimension() == 4);
        } finally {
            // collection drop 不支持,leave
        }
    }

    @Test
    void describeIndex_matchesGetIndexStats() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_qdrant_hs_describe");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(8);
        config.setMetric("l2");
        vectorService.createIndex(indexName, config);

        try {
            IndexInfo described = vectorService.describeIndex(indexName);
            IndexInfo stats = vectorService.getIndexStats(indexName);

            // describeIndex 在 Qdrant impl 中委托 getIndexStatsImpl,两者应当完全等价
            assertThat(described.name()).isEqualTo(stats.name());
            assertThat(described.dimension()).isEqualTo(stats.dimension());
            assertThat(described.metric()).isEqualToIgnoringCase(stats.metric());
            assertThat(described.documentCount()).isEqualTo(stats.documentCount());
        } finally {
            // collection drop 不支持,leave
        }
    }
}