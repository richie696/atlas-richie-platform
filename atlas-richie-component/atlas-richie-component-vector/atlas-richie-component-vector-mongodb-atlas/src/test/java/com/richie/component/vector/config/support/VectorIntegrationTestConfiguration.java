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
package com.richie.component.vector.config.support;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.service.RerankService;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * MongoDB Atlas Vector Search 集成测试上下文。
 * <p>
 * 通过 {@code @TestPropertySource(properties = "spring.autoconfigure.exclude=...")} 在 IT 类
 * 上排除无关 framework 自动装配（如 tenant 需要的 JSQLParser 缺失、cache/redis 模块的额外配置）。
 * 这里仅保留 Spring Boot 默认的 {@code spring-boot-starter-data-mongodb} 自动装配
 * 即可创建 {@code MongoTemplate}，与 {@code MongoDbAtlasVectorAutoConfiguration} 需要的
 * {@code MongoTemplate} Bean 一致。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.vector.config.MongoDbAtlasVectorAutoConfiguration.class,
})
public class VectorIntegrationTestConfiguration {

    /**
     * 固定 4 维向量 stub。
     * <p>
     * 镜像 Milvus/PG/Redis IT 的约定 — IT 环境无 {@code aiEmbeddingModel} Bean 时由
     * 显式 {@code @Bean("aiEmbeddingModel")} 提供。MongoDbAtlasVectorServiceImpl
     * 构造函数依赖 {@code @Qualifier("aiEmbeddingModel") EmbeddingModel}。
     */
    @Bean("aiEmbeddingModel")
    @Primary
    public EmbeddingModel aiEmbeddingModel() {
        return new MongoStubEmbeddingModel();
    }

    /**
     * RerankService 占位 — AbstractVectorService 构造器要求可空注入，IT 上下文里提供
     * 匿名实现以满足非 null 注入并禁用重排（rerank(rerankService == null) → 直接返回结果）。
     */
    @Bean
    public RerankService rerankService() {
        return new RerankService() {
            @Override
            public RerankResponse rerank(String query, List<String> documents, String model, Integer topN) {
                return RerankResponse.succeed(Collections.emptyList(), Clock.systemDefaultZone());
            }

            @Override
            public CompletableFuture<RerankResponse> rerankAsync(String query, List<String> documents, String model, Integer topN) {
                return CompletableFuture.completedFuture(
                        RerankResponse.succeed(Collections.emptyList(), Clock.systemDefaultZone()));
            }
        };
    }

    /**
     * RerankModel 占位 — 与 rerankService 并存是为了避免某些模块（如 ai 自身组件扫描）
     * 因缺失 RerankModel Bean 启动失败。
     */
    @Bean
    public RerankModel rerankModel() {
        return new RerankModel() {
            @Override
            public RerankResponse rerank(RerankRequest request) {
                return RerankResponse.succeed(Collections.emptyList(), Clock.systemDefaultZone());
            }

            @Override
            public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
                return CompletableFuture.completedFuture(
                        RerankResponse.succeed(Collections.emptyList(), Clock.systemDefaultZone()));
            }
        };
    }

    private static final class MongoStubEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
            List<org.springframework.ai.embedding.Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new org.springframework.ai.embedding.Embedding(
                        new float[]{0.1f, 0.2f, 0.3f, 0.4f}, i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }

        @Override
        public float[] embed(String text) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> new float[]{0.0f, 0.0f, 0.0f, 0.0f}).toList();
        }

        @Override
        public int dimensions() {
            return 4;
        }
    }
}
