package com.richie.component.mongodb.config;

import com.mongodb.connection.ServerMonitoringMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MongoDB 配置类
 * 支持基础连接、认证、连接池、超时、心跳、监控、SSL等生产级参数配置
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01 16:20:52
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mongodb")
public class MongodbConfig {

    // ========== 基础连接配置 ==========
    /**
     * MongoDB连接URI（优先级高于host/port等单独参数，适合Atlas集群或SRV连接）
     */
    private String uri;

    /**
     * 主机名（如localhost或Atlas节点域名）
     */
    private String host = "localhost";

    /**
     * 端口号
     */
    private Integer port = 27017;

    /**
     * 目标数据库名称
     */
    private String database = "example";

    // ========== 认证配置 ==========
    /**
     * 认证数据库（如admin，默认为admin）
     */
    private String authDatabase = "admin";

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    // ========== 连接池与超时配置 ==========
    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectTimeoutMs = 10000;

    /**
     * 读取超时时间（毫秒）
     */
    private Integer socketTimeoutMs = 10000;

    /**
     * 连接池最大等待时间（毫秒）
     */
    private Integer maxWaitTimeMs = 2000;

    /**
     * 连接池最大空闲时间（毫秒）
     */
    private Integer connectionIdleTime = 0;

    /**
     * 连接池最大连接数
     */
    private Integer maxConnectionPoolSize = 100;

    /**
     * 连接池最小连接数
     */
    private Integer minConnectionPoolSize = 0;

    /**
     * 最大并发连接数（新建连接时最大并发数）
     */
    private Integer maxConnecting = 2;

    /**
     * 连接池最大连接数（兼容旧参数名maxPoolSize）
     */
    private Integer maxPoolSize = 20;

    /**
     * 连接池最小连接数（兼容旧参数名minPoolSize）
     */
    private Integer minPoolSize = 5;

    // ========== 心跳与监控配置 ==========
    /**
     * 心跳检测频率（毫秒）
     */
    private long heartbeatFrequency = 10000;

    /**
     * 最小心跳检测频率（毫秒）
     */
    private long minHeartbeatFrequency = 500;

    /**
     * 服务器监控模式（AUTO/SCANNER/COMMAND）
     */
    private ServerMonitoringMode serverMonitoringMode = ServerMonitoringMode.AUTO;

    // ========== SSL/TLS安全配置 ==========
    /**
     * 是否启用SSL/TLS安全连接
     */
    private boolean sslEnabled = false;

    /**
     * 是否允许主机名不匹配（仅测试环境建议开启）
     */
    private boolean invalidHostNameAllowed = false;

    /**
     * SSL证书文件路径（JKS格式）
     */
    private String sslCertificatePath;

    /**
     * SSL证书密码
     */
    private String sslCertificatePassword;

}
