/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.config.support;

import com.richie.component.vector.config.RedisVectorAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(RedisVectorAutoConfiguration.class)
public class VectorIntegrationTestConfiguration {

    /**
     * 固定 4 维向量 stub。生产环境的 aiEmbeddingModel 由 atlas-richie-component-ai 的 AiModelAutoConfiguration
     * 提供,要求 platform.component.ai.config-initialization-enabled=true 且 models 非空;测试环境无此配置,
     * 显式提供同名 bean 以满足 RedisVectorServiceImpl @Qualifier("aiEmbeddingModel") 的注入需求。
     */
    @Bean("aiEmbeddingModel")
    public EmbeddingModel aiEmbeddingModel() {
        return new StubEmbeddingModel();
    }

    private static final class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
            List<org.springframework.ai.embedding.Embedding> embeddings = new java.util.ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new org.springframework.ai.embedding.Embedding(
                        new float[]{0.1f * (i + 1), 0.2f * (i + 1), 0.3f * (i + 1), 0.4f * (i + 1)}, i));
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
