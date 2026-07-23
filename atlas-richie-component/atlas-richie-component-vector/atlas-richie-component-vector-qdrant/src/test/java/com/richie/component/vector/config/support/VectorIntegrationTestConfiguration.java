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
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.vector.config.QdrantVectorAutoConfiguration.class,
})
@TestPropertySource(properties = {
        // 关闭 Spring AI 各厂商 chat 模型自动装配 — 这些 starter 由 spring-ai-starter-vector-store-qdrant
        // 传递引入,但缺少完整的 http 客户端实现(okhttp-jvm),会导致 AnthropicChatModel 等 bean
        // 在上下文启动时尝试实例化并触发 NoClassDefFoundError。
        // 设置为 "none" 让 ConditionalOnProperty(havingValue="anthropic") 不匹配,跳过该 autoconfig。
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.embedding.text=none"
})
public class VectorIntegrationTestConfiguration {

    /**
     * 固定 4 维向量 stub。
     * <p>
     * 镜像 Milvus IT 的 StubEmbeddingModel 约定,生产环境由 atlas-richie-component-ai 的
     * AiModelAutoConfiguration 提供 {@code aiEmbeddingModel},IT 默认环境无该 bean。
     * QdRantVectorServiceImpl 构造函数依赖 {@code @Qualifier("aiEmbeddingModel") EmbeddingModel},
     * 测试环境必须显式提供同名 bean 才能完成 Spring 上下文装配。
     */
    @Bean("aiEmbeddingModel")
    @Primary
    public EmbeddingModel aiEmbeddingModel() {
        return new QdrantStubEmbeddingModel();
    }

    /**
     * RerankModel 占位 — atlas-richie-component-ai 的 {@code RerankServiceImpl} 是 {@code @Service},
     * 通过 {@code com.richie.component.vector} 包扫描自动注册,IT 上下文里需要提供一个非 null Bean
     * 才能满足构造注入。RerankModel 接口无 default 方法,使用匿名内部类同时实现 rerank / rerankAsync。
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

    private static final class QdrantStubEmbeddingModel implements EmbeddingModel {

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