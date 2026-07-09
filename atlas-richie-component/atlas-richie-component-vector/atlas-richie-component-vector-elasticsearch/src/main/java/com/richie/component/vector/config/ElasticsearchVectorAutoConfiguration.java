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

import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch向量数据库自动配置类
 * 只注入唯一的DefaultVectorServiceImpl，自动适配所有Spring AI支持的向量数据库
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({VectorProperties.class, ElasticsearchConfig.class})
public class ElasticsearchVectorAutoConfiguration {

    /**
     * Elasticsearch 向量数据库自动装配
     * 依赖：spring-ai-starter-vector-store-elasticsearch
     * 典型配置：spring.elasticsearch.* + spring.ai.vectorstore.elasticsearch.*
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "elasticsearch")
    public VectorStore elasticsearchVectorStore(Rest5Client restClient, EmbeddingModel embeddingModel) {
        return ElasticsearchVectorStore.builder(restClient, embeddingModel).build();
    }

    /**
     * 创建 Rest5Client Bean
     * <p>
     * Rest5Client 参数映射说明：
     * - clusterUrl ✅ → URI 作为 builder 参数
     * - connectTimeout ✅ → setConnectionConfigCallback.setConnectTimeout
     * - socketTimeout ✅ → setConnectionConfigCallback.setSocketTimeout
     * - contentCompressionEnabled ✅ → setCompressionEnabled
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "elasticsearch")
    public Rest5Client restClient(ElasticsearchConfig config) {
        Rest5ClientBuilder builder = Rest5Client.builder(URI.create(config.getClusterUrl()));

        builder.setConnectionConfigCallback(connectConf -> connectConf
                .setConnectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .setSocketTimeout(config.getSocketTimeout(), TimeUnit.MILLISECONDS)
        );

        builder.setCompressionEnabled(config.isContentCompressionEnabled());

        return builder.build();
    }
}
