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
import com.richie.component.vector.config.support.AbstractVectorRedisIntegrationTest;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "platform.component.vector.provider=redis",
        "platform.component.vector.default-index=it-vectors",
        "spring.ai.vectorstore.redis.index-name=it-vectors",
        "spring.ai.vectorstore.redis.prefix=it-v:",
        "spring.ai.vectorstore.redis.initialize-schema=true"
})
class RedisVectorRecordOpsIT extends AbstractVectorRedisIntegrationTest {

    @Autowired(required = false)
    private VectorService vectorService;

    @Autowired(required = false)
    private VectorProperties vectorProperties;

    @BeforeEach
    void ensureIndex() {
        if (vectorService == null) {
            return;
        }
        // Spring AI 的 RedisVectorStore.add() 不会创建索引,只往现有索引写;
        // 显式调用 createIndex 幂等建立测试索引,匹配 VectorServiceImpl.add / searchByText 的工作前提。
        VectorProperties.IndexConfig config = null;
        if (vectorProperties != null && vectorProperties.getIndexes() != null) {
            config = vectorProperties.getIndexes().get("it-vectors");
        }
        if (config == null) {
            config = new VectorProperties.IndexConfig();
            config.setDimension(4);
            config.setMetric("cosine");
        }
        vectorService.createIndex("it-vectors", config);
    }

    @Test
    void addAndSearchDocument_whenVectorServicePresent() {
        if (vectorService == null) {
            return;
        }
        // v2 VectorService 提供 addText 门面,内部走 AbstractVectorService.embedText → addEmbeddings 链路
        String id = vectorService.addText("it-vectors", "wave-b integration sample", new HashMap<>());
        assertThat(id).isNotBlank();
        // searchByText 由 AbstractVectorService.searchByText 提供,直接调 Spring AI VectorStore。
        List<?> hits = vectorService.searchByText("it-vectors", "integration", 3);
        assertThat(hits).isNotNull();
    }
}
