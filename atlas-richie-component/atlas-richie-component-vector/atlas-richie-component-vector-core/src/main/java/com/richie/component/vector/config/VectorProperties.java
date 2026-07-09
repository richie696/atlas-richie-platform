/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.config;

import com.richie.component.vector.enums.EmbeddingProvider;
import com.richie.component.vector.enums.VectorProvider;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 向量数据库配置属性
 * 用于配置不同向量数据库的连接参数和索引设置
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector")
public class VectorProperties {

    /**
     * 启用的向量数据库提供商
     */
    private VectorProvider provider = VectorProvider.REDIS;

    /** Embedding 模型提供商（OpenAI / 智谱 / Ollama） */
    private EmbeddingProvider embeddingProvider = EmbeddingProvider.OPENAI;

    /**
     * OpenAI API密钥
     */
    private String apiKey;

    /**
     * 默认索引名称
     */
    private String defaultIndex = "documents";

    /**
     * 索引配置映射
     */
    private Map<String, IndexConfig> indexes;

    /**
     * Ollama配置
     */
    private OllamaConfig ollama = new OllamaConfig();


    /**
     * 索引配置类
     */
    @Data
    @Accessors(chain = true)
    public static class IndexConfig {
        /**
         * 索引名称
         */
        private String name;

        /**
         * 向量维度
         */
        private Integer dimension = 1536;

        /**
         * 距离度量方式
         */
        private String metric = "cosine";

        /**
         * 索引类型
         */
        private String indexType = "hnsw";

        /**
         * 副本数量
         */
        private Integer replicas = 1;

        /**
         * 分片数量
         */
        private Integer shards = 1;

        /**
         * 额外字段配置
         * 用于动态添加自定义字段
         */
        private Map<String, Object> additionalFields;

        /**
         * 索引参数配置
         * 用于配置不同索引类型的特定参数
         */
        private Map<String, Object> indexParams;

    }

    /**
     * Ollama配置类
     */
    @Data
    @Accessors(chain = true)
    public static class OllamaConfig {
        /**
         * Ollama服务基础URL
         */
        private String baseUrl = "http://localhost:11434";

        /**
         * 模型名称
         */
        private String model = "llama2";

    }
}
