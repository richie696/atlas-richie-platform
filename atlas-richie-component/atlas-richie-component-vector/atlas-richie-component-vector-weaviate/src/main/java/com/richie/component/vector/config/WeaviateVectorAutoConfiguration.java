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

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStoreOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Weaviate 向量库自动配置（当 provider=weaviate 时注册 VectorStore 与 WeaviateClient）。
 *
 * @author richie696
 * @since 2025-07-01
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(WeaviateConfig.class)
public class WeaviateVectorAutoConfiguration {

    /**
     * Weaviate VectorStore Bean（依赖 spring-ai-starter-vector-store-weaviate）。
     *
     * @param embeddingModel  Embedding 模型
     * @param weaviateClient Weaviate 客户端
     * @param config         Weaviate 配置
     * @return WeaviateVectorStore 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "weaviate")
    @Autowired
    public VectorStore weaviateVectorStore(EmbeddingModel embeddingModel, WeaviateClient weaviateClient, WeaviateConfig config) {
        // 解析filterMetadataFields配置，支持格式如 country:text,year:number
        List<WeaviateVectorStore.MetadataField> metadataFields = List.of();
        if (config.getFilterMetadataFields() != null && !config.getFilterMetadataFields().isBlank()) {
            metadataFields = java.util.Arrays.stream(config.getFilterMetadataFields().split(","))
                    .map(pair -> {
                        String[] arr = pair.split(":");
                        if (arr.length == 2) {
                            if ("text".equalsIgnoreCase(arr[1])) {
                                return WeaviateVectorStore.MetadataField.text(arr[0]);
                            } else if ("number".equalsIgnoreCase(arr[1])) {
                                return WeaviateVectorStore.MetadataField.number(arr[0]);
                            }
                        }
                        return null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        // Spring AI 1.0+: 使用 WeaviateVectorStoreOptions 配置
        WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();
        options.setObjectClass(config.getObjectClass());

        // 创建Weaviate向量存储对象，参数全部由配置文件驱动
        return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
                // 使用 Options 对象设置 objectClass
                .options(options)
                // 从配置读取一致性级别
                .consistencyLevel(config.getConsistencyLevel())
                // 从配置读取可过滤元数据字段
                .filterMetadataFields(metadataFields)
                .build();
    }

    /**
     * Weaviate 客户端 Bean。
     *
     * @param config Weaviate 连接配置（scheme、host）
     * @return WeaviateClient 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "weaviate")
    public WeaviateClient weaviateClient(WeaviateConfig config) {
        Config weaviateConfig = new Config(config.getScheme(), config.getHost());
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            try {
                return WeaviateAuthClient.apiKey(weaviateConfig, config.getApiKey());
            } catch (AuthException e) {
                throw new RuntimeException("Weaviate 认证失败: " + e.getMessage(), e);
            }
        }
        return new WeaviateClient(weaviateConfig);
    }
}
