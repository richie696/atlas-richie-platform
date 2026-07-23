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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.vector.config.PostgresqlVectorAutoConfiguration.class,
})
public class VectorIntegrationTestConfiguration {

    /**
     * 固定 4 维向量 stub,详见 redis 模块对应文件注释。
     */
    @Bean("aiEmbeddingModel")
    @Primary
    public EmbeddingModel aiEmbeddingModel() {
        return new PgStubEmbeddingModel();
    }

    /**
     * RerankModel 占位 — atlas-richie-component-ai 的 {@code RerankServiceImpl} 是 {@code @Service},
     * 组件扫描自动注册,IT 上下文里需要提供一个非 null Bean 才能满足构造注入。
     * RerankModel 接口无 default 方法,使用匿名内部类同时实现 rerank / rerankAsync。
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

    private static final class PgStubEmbeddingModel implements EmbeddingModel {

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
