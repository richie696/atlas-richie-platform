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

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量数据库自动配置类
 * 只注入唯一的DefaultVectorServiceImpl，自动适配所有Spring AI支持的向量数据库
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({VectorProperties.class, MilvusConfig.class})
public class MilvusVectorAutoConfiguration {

    /**
     * Milvus 向量数据库自动装配
     * 依赖：spring-ai-starter-vector-store-milvus
     * 典型配置：spring.ai.vectorstore.milvus.*
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "milvus")
    public VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel, MilvusConfig config) {
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .databaseName(config.getDatabaseName())
                .collectionName(config.getCollectionName())
                .indexType(config.getIndexType())
                .metricType(config.getMetricType())
                .batchingStrategy(new TokenCountBatchingStrategy())
                .initializeSchema(true)
                .build();
    }

    @Bean
    public MilvusServiceClient milvusClient(MilvusConfig config) {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withUri(config.getHost())
                .withPort(config.getPort())
                .withConnectTimeout(config.getConnectTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTime(config.getKeepAliveTimeMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(config.getKeepAliveTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .withIdleTimeout(config.getIdleTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);

        // 认证配置
        if (config.getUsername() != null && config.getPassword() != null) {
            builder.withAuthorization(config.getUsername(), config.getPassword());
        }

        // SSL配置
        if (config.isSecure()) {
            if (config.getServerPemPath() != null) {
                builder.withServerPemPath(config.getServerPemPath());
            }
            if (config.getServerName() != null) {
                builder.withServerName(config.getServerName());
            }
            if (config.getCaPemPath() != null) {
                builder.withCaPemPath(config.getCaPemPath());
            }
            if (config.getClientKeyPath() != null && config.getClientPemPath() != null) {
                builder.withClientKeyPath(config.getClientKeyPath())
                        .withClientPemPath(config.getClientPemPath());
            }
        }

        return new MilvusServiceClient(builder.build());
    }
}
