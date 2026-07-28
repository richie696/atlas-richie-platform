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
package cn.richie696.component.vector.config;

import lombok.Data;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Weaviate 向量库的连接与 schema 配置。
 *
 * <p>该类型是 Weaviate provider 模块的 {@code @ConfigurationProperties} 载体，绑定
 * {@code platform.component.vector.weaviate.*} 命名空间下的配置项，供
 * {@link WeaviateVectorAutoConfiguration} 构造 {@code WeaviateClient} 与
 * {@code WeaviateVectorStore} 时读取。它只描述“如何连上 Weaviate”以及“把数据写到哪个
 * class”，通用索引维度、距离度量、批量流水线等仍由 {@code core} 模块的
 * {@code VectorProperties} 统一表达，二者通过 {@link cn.richie696.component.vector.service.VectorService}
 * 实现类在 Spring 容器内组合。</p>
 *
 * @author richie696
 * @since 2025-07-01
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.weaviate")
public class WeaviateConfig {

    /**
     * 连接协议（{@code http} 或 {@code https}），用于构造 {@code WeaviateClient}。
     */
    private String scheme;
    /**
     * 服务地址
     */
    private String host;

    /**
     * API Key，用于连接需要认证的 Weaviate 实例
     */
    private String apiKey;

    /**
     * Weaviate中用于存储向量的Class名称（类似表名）
     */
    private String objectClass = "CustomClass";

    /**
     * 一致性级别（如: ONE, QUORUM, ALL）
     */
    private WeaviateVectorStore.ConsistentLevel consistencyLevel = WeaviateVectorStore.ConsistentLevel.QUORUM;

    /**
     * 可过滤的元数据字段，格式如 country:text,year:number
     */
    private String filterMetadataFields = "country:text,year:number";

}
