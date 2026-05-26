package com.richie.component.vector.config;

import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus向量数据库配置属性
 * 用于配置Milvus连接参数、索引设置和认证信息
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.milvus")
public class MilvusConfig {

    /**
     * 服务地址
     */
    private String host = "localhost";

    /**
     * 服务端口
     */
    private int port = 19530;

    /**
     * 用户名（可选）
     */
    private String username;

    /**
     * 密码（可选）
     */
    private String password;

    /**
     * 数据库名称
     */
    private String databaseName = "default";

    /**
     * 集合名称
     */
    private String collectionName = "documents";

    /**
     * 向量维度
     */
    private int embeddingDimension = 1536;

    /**
     * 索引类型
     */
    private IndexType indexType = IndexType.IVF_FLAT;

    /**
     * 距离度量类型
     */
    private MetricType metricType = MetricType.COSINE;

    // ========== 连接配置 ==========

    /**
     * 连接超时时间（毫秒）
     */
    private long connectTimeoutMs = 10000;

    /**
     * Keep-Alive时间（毫秒）
     */
    private long keepAliveTimeMs = 30000;

    /**
     * Keep-Alive超时时间（毫秒）
     */
    private long keepAliveTimeoutMs = 10000;

    /**
     * 是否在没有调用时保持连接
     */
    private boolean keepAliveWithoutCalls = true;

    /**
     * 空闲超时时间（毫秒）
     */
    private long idleTimeoutMs = 300000;

    // ========== SSL/TLS配置 ==========

    /**
     * 是否启用SSL/TLS安全连接
     */
    private boolean secure = false;

    /**
     * 服务器PEM证书路径
     */
    private String serverPemPath;

    /**
     * 服务器名称（用于SSL验证）
     */
    private String serverName;

    /**
     * CA证书路径
     */
    private String caPemPath;

    /**
     * 客户端私钥路径
     */
    private String clientKeyPath;

    /**
     * 客户端证书路径
     */
    private String clientPemPath;

}

