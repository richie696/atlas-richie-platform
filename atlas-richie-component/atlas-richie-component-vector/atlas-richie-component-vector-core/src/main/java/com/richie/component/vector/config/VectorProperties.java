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
     * 批量管线配置 — 按 §6.1 设计文档定义
     * <p>
     * 控制 {@code AbstractVectorService.runBatchPipeline} 的并发度、批量大小、背压与容错策略。
     * 详见 <a href="https://github.com/richie696/atlas-richie-platform/blob/main/atlas-richie-component/atlas-richie-component-vector/VECTOR_SERVICE_V2_DESIGN.md#61-配置项">VECTOR_SERVICE_V2_DESIGN §6.1</a>。
     */
    private Batch batch = new Batch();


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

    /**
     * 批量管线配置 — 对应 {@code platform.component.vector.batch.*}。
     * <p>
     * 字段定义严格遵循 §6.1 配置表；任何字段缺失时使用本类字段初始化器作为兜底，
     * 确保 {@code new VectorProperties().getBatch()} 即可获得完整默认值。
     * <p>
     * <b>继承关系</b>：未启用 Spring Boot 配置绑定（{@code @ConfigurationProperties}）时，
     * 本类仍可通过 {@code new VectorProperties.Batch()} 单独构造并直接传入
     * {@code AbstractVectorService} 的新构造器。
     */
    @Data
    @Accessors(chain = true)
    public static class Batch {

        /**
         * 一次 {@code EmbeddingModel.embed(List)} 传入的记录数。
         */
        private int embeddingBatchSize = 32;

        /**
         * 同时进行的嵌入调用数（按 provider 容量调）。
         */
        private int embeddingConcurrency = 8;

        /**
         * 一次 {@code VectorStore.add(List)} 传入的记录数。
         */
        private int writeBatchSize = 100;

        /**
         * 同时进行的写入调用数。
         */
        private int writeConcurrency = 4;

        /**
         * {@code Sinks.Many} 背压缓冲上限（Phase A 暂未启用 Sinks 重写，留作占位）。
         */
        private int backpressureBuffer = 1024;

        /**
         * 单条失败是否中断批次；{@code false} 时保持现有 continue 行为。
         */
        private boolean failFast = false;

        /**
         * 内容哈希去重 LRU 大小（{@code 0} 禁用）。
         * <p>
         * Phase A 简化：使用 {@code ConcurrentHashMap.newKeySet()} 而非完整 LRU —
         * 命中即可去重，不淘汰冷数据（业务侧可结合外部缓存自行管理生命周期）。
         */
        private int dedupCacheSize = 10_000;

        /**
         * itemId 取值来源：{@link ItemIdSource#METADATA} / {@link ItemIdSource#ID} / {@link ItemIdSource#HASH}。
         */
        private ItemIdSource itemIdSource = ItemIdSource.METADATA;

        /**
         * itemId 来源枚举 — 与 §6.1 配置表三种模式一一对应。
         * <ul>
         *   <li>{@link #METADATA}：{@code record.getMetadata().__itemId}（默认，兼容旧行为）</li>
         *   <li>{@link #ID}：{@code record.getId()}（用于 metadata 不携带业务 ID 的场景）</li>
         *   <li>{@link #HASH}：内容 SHA-256 摘要（用于完全以内容寻址的去重场景）</li>
         * </ul>
         */
        public enum ItemIdSource { METADATA, ID, HASH }
    }
}
