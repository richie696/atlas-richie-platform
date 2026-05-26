package com.richie.component.vector.config;

import lombok.Data;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "platform.component.vector.weaviate")
public class WeaviateConfig {

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
