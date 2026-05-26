package com.richie.component.vector.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
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
@EnableConfigurationProperties(QdRantConfig.class)
public class QdrantVectorAutoConfiguration {

    /**
     * Qdrant 向量数据库自动装配
     * 依赖：spring-ai-starter-vector-store-qdrant
     * 典型配置：spring.ai.vectorstore.qdrant.*
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "qdrant")
    public VectorStore qdrantVectorStore(EmbeddingModel embeddingModel, QdRantConfig config, QdrantClient qdrantClient) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(config.getCollection())
                .initializeSchema(config.isInitializeSchema())
                .build();
    }

    /**
     * QdrantClient 客户端实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "qdrant")
    public QdrantClient qdrantClient(QdRantConfig config) {
        return new QdrantClient(QdrantGrpcClient.newBuilder(
                config.getHost(),
                config.getPort(),
                config.isUseTransportLayerSecurity()
        ).build());
    }

}
