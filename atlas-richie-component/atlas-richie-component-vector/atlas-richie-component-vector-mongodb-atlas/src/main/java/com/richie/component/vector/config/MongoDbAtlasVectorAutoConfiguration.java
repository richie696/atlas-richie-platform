package com.richie.component.vector.config;

import com.richie.component.mongodb.config.MongodbConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mongodb.atlas.MongoDBAtlasVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB Atlas 向量数据库自动配置
 * 负责自动装配 MongoTemplate、MongoDBAtlasVectorStore 及 SSL 相关配置，
 * 支持用户名密码认证、连接池、心跳、SSL证书等生产级参数，
 * 便于在 Spring 环境下安全高效地集成 MongoDB 向量存储。
 *
 * @author richie696
 * @since 2025-07-04 16:20:52
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({VectorProperties.class, MongodbConfig.class})
public class MongoDbAtlasVectorAutoConfiguration {

    /**
     * 向量存储Bean自动注入
     * 依赖 spring-ai-starter-vector-store-mongodb-atlas
     * 典型配置：spring.data.mongodb.* + spring.ai.vectorstore.mongodb.*
     *
     * @param mongoTemplate  MongoTemplate实例
     * @param embeddingModel 嵌入模型
     * @return MongoDBAtlasVectorStore 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "mongodb")
    public VectorStore mongoVectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
        return MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel).build();
    }

}
