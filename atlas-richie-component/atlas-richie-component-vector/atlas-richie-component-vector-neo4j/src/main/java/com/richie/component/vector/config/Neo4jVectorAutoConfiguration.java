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

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j向量数据库自动配置类
 * 提供生产环境就绪的Neo4j Driver配置，包括连接池管理、监控、容错等特性
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-04
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(Neo4jConfig.class)
public class Neo4jVectorAutoConfiguration {

    /**
     * Neo4j 向量数据库自动装配
     * 依赖：spring-ai-starter-vector-store-neo4j
     * 典型配置：spring.data.neo4j.* + spring.ai.vectorstore.neo4j.*
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "neo4j")
    public VectorStore neo4jVectorStore(Driver neo4jClient, EmbeddingModel embeddingModel) {
        return Neo4jVectorStore.builder(neo4jClient, embeddingModel).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "neo4j")
    public Driver originalNeo4jDriver(Neo4jConfig config) {
        log.info("初始化Neo4j Driver，应用名称: {}", config.getApplicationName());

        var configBuilder = Config.builder()
                // 连接池配置
                .withMaxConnectionPoolSize(config.getMaxConnectionPoolSize())
                .withMaxConnectionLifetime(config.getMaxConnectionLifetimeMillis(), TimeUnit.MILLISECONDS)
                .withConnectionAcquisitionTimeout(config.getConnectionAcquisitionTimeoutMillis(), TimeUnit.MILLISECONDS)
                .withConnectionTimeout(config.getConnectionTimeoutMillis(), TimeUnit.MILLISECONDS)
                // 会话配置
                .withMaxTransactionRetryTime(config.getMaxTransactionRetryTimeMillis(), TimeUnit.MILLISECONDS)
                // 应用名称
                .withUserAgent(config.getApplicationName());

        // 加密和安全配置
        if (config.isEncryptionEnabled()) {
            configBuilder.withEncryption();
            configBuilder.withTrustStrategy(Config.TrustStrategy.trustSystemCertificates());
        } else {
            configBuilder.withoutEncryption();
        }

        // 日志配置
        configBuilder.withLogging(Logging.slf4j());

        // 记录配置信息
        log.info("Neo4j配置 - 连接池大小: {}, 加密: {}, 重试策略: {}",
                config.getMaxConnectionPoolSize(),
                config.isEncryptionEnabled(),
                config.getRetryStrategy());

        Driver driver = GraphDatabase.driver(
                config.getUri(),
                AuthTokens.basic(config.getUsername(), config.getPassword()),
                configBuilder.build()
        );

        log.info("Neo4j Driver初始化完成，连接池大小: {}", config.getMaxConnectionPoolSize());

        return driver;
    }

}

