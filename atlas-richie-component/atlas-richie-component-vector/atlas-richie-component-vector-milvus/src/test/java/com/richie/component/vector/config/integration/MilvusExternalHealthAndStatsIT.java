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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milvus 单用途集成测试（外部 Milvus，无 Testcontainers） — 覆盖 healthCheck + getIndexStats + listIndexes + describeIndex。
 * <p>
 * 与 {@link MilvusHealthAndStatsIT} 的关键差异:直接连接到由 {@code MILVUS_HOST}:{@code MILVUS_PORT}
 * 指向的真实实例，未设置环境变量或设置了 {@code SKIP_TESTCONTAINERS=true} 时自动跳过。
 * 用于 CI 早期冒烟测试或本地调试已有 Milvus 实例的场景。
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
class MilvusExternalHealthAndStatsIT {

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
        String indexName = uniqueIndex("it_ext_milvus_hs_consistent");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("COSINE");
        vectorService.createIndex(indexName, config);

        try {
            // healthCheck 与 getIndexStats 协同 — 同一个 collection 在 3 秒探针窗口内应同时报告健康/统计
            assertThat(vectorService.healthCheck(indexName)).isTrue();

            IndexInfo stats = vectorService.getIndexStats(indexName);
            assertThat(stats).isNotNull();
            assertThat(stats.name()).isEqualTo(indexName);
            assertThat(stats.dimension()).isEqualTo(4);
            assertThat(stats.metric()).isEqualToIgnoringCase("COSINE");
            assertThat(stats.documentCount()).isZero();
        } finally {
            // Milvus 支持 deleteIndex,显式清理
            try {
                vectorService.deleteIndex(indexName);
            } catch (Exception ignored) {
                // 幂等兜底
            }
        }
    }

    @Test
    void listIndexes_afterCreate_containsCreated() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_hs_list");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(4);
        config.setMetric("COSINE");
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
            try {
                vectorService.deleteIndex(indexName);
            } catch (Exception ignored) {
                // 幂等兜底
            }
        }
    }

    @Test
    void describeIndex_matchesGetIndexStats() {
        if (vectorService == null) {
            return;
        }
        String indexName = uniqueIndex("it_ext_milvus_hs_describe");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(8);
        config.setMetric("IP");
        vectorService.createIndex(indexName, config);

        try {
            IndexInfo described = vectorService.describeIndex(indexName);
            IndexInfo stats = vectorService.getIndexStats(indexName);

            // describeIndex 与 getIndexStats 在 Milvus impl 中应当一致,核心字段逐一比对
            assertThat(described.name()).isEqualTo(stats.name());
            assertThat(described.dimension()).isEqualTo(stats.dimension());
            assertThat(described.metric()).isEqualToIgnoringCase(stats.metric());
            assertThat(described.documentCount()).isEqualTo(stats.documentCount());
        } finally {
            try {
                vectorService.deleteIndex(indexName);
            } catch (Exception ignored) {
                // 幂等兜底
            }
        }
    }
}
