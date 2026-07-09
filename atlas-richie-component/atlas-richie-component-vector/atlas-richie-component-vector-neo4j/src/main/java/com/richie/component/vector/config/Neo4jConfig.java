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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Neo4j向量数据库配置属性
 * 用于配置Neo4j连接参数、连接池设置、安全配置和监控选项
 * 支持生产环境的最佳实践配置
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-04
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.neo4j")
public class Neo4jConfig {

    // ==================== 基础连接配置 ====================

    /**
     * Neo4j连接URI
     * 支持单节点: bolt://localhost:7687
     * 支持集群: bolt+routing://localhost:7687
     * 支持Neo4j AuraDB: neo4j+s://your-instance.neo4j.io:7687
     */
    private String uri = "bolt://localhost:7687";

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 数据库名称（Neo4j 4.0+支持多数据库）
     */
    private String database = "neo4j";

    // ==================== 连接池配置 ====================

    /**
     * 最大连接池大小
     * 生产环境建议: 50-100，根据并发量和服务器资源调整
     */
    private int maxConnectionPoolSize = 50;

    /**
     * 连接最大生命周期（毫秒）
     * 生产环境建议: 1-2小时，避免连接过长时间占用
     */
    private long maxConnectionLifetimeMillis = 60 * 60 * 1000L; // 1小时

    /**
     * 连接空闲超时时间（毫秒）
     * 生产环境建议: 15-30分钟，及时释放空闲连接
     */
    private long connectionIdleTimeoutMillis = 30 * 60 * 1000L; // 30分钟

    /**
     * 获取连接超时时间（毫秒）
     * 生产环境建议: 10-30秒，避免长时间等待
     */
    private long connectionAcquisitionTimeoutMillis = 10 * 1000L; // 10秒

    /**
     * 连接超时时间（毫秒）
     * 生产环境建议: 5-10秒，快速失败
     */
    private long connectionTimeoutMillis = 5 * 1000L; // 5秒

    /**
     * 是否启用连接验证
     * 生产环境建议: true，确保连接有效性
     */
    private boolean connectionValidationEnabled = true;

    /**
     * 连接验证查询
     * 用于验证连接是否有效的Cypher查询
     */
    private String connectionValidationQuery = "RETURN 1";

    /**
     * 连接验证超时时间（毫秒）
     * 生产环境建议: 3-5秒
     */
    private long connectionValidationTimeoutMillis = 3 * 1000L; // 3秒

    // ==================== 负载均衡配置 ====================

    /**
     * 是否启用负载均衡
     * 集群环境建议: true
     */
    private boolean loadBalancingEnabled = false;

    /**
     * 负载均衡策略
     * 注意：Neo4j Driver的负载均衡主要通过URI配置实现
     * 集群URI格式：neo4j://host1:port1,host2:port2,host3:port3
     * 或者使用bolt+routing://协议
     * 此配置主要用于日志记录和监控
     */
    private String loadBalancingStrategy = "least_connected";

    /**
     * 负载均衡器最大连接数
     */
    private int loadBalancerMaxConnectionPoolSize = 100;

    // ==================== 重试和容错配置 ====================

    /**
     * 重试策略类型
     * 可选值: exponential_backoff, fixed_delay
     */
    private String retryStrategy = "exponential_backoff";

    /**
     * 最大重试次数
     * 生产环境建议: 3-5次
     */
    private int maxRetryAttempts = 3;

    /**
     * 重试延迟时间（毫秒）
     */
    private long retryDelayMillis = 1000L; // 1秒

    /**
     * 最大重试延迟时间（毫秒）
     */
    private long maxRetryDelayMillis = 30 * 1000L; // 30秒

    /**
     * 是否启用断路器
     * 生产环境建议: true，防止级联故障
     */
    private boolean circuitBreakerEnabled = true;

    /**
     * 断路器失败阈值
     * 连续失败多少次后触发断路器
     */
    private int circuitBreakerFailureThreshold = 5;

    /**
     * 断路器恢复时间（毫秒）
     * 断路器打开后多久尝试恢复
     */
    private long circuitBreakerRecoveryTimeMillis = 60 * 1000L; // 1分钟

    // ==================== 安全配置 ====================

    /**
     * 是否启用加密
     * 生产环境建议: true
     */
    private boolean encryptionEnabled = true;

    /**
     * 信任策略类型
     * 可选值: trust_system_certificates, trust_all_certificates, trust_custom_ca_signed_certificates
     */
    private String trustStrategy = "trust_system_certificates";

    /**
     * 自定义CA证书路径
     */
    private String customCaCertificatePath;

    /**
     * 是否启用客户端证书
     */
    private boolean clientCertificateEnabled = false;

    /**
     * 客户端证书路径
     */
    private String clientCertificatePath;

    /**
     * 客户端私钥路径
     */
    private String clientPrivateKeyPath;

    /**
     * 客户端证书密码
     */
    private String clientCertificatePassword;

    /**
     * 是否启用Kerberos认证
     */
    private boolean kerberosEnabled = false;

    /**
     * Kerberos服务主体名称
     */
    private String kerberosServicePrincipalName;

    // ==================== 会话和事务配置 ====================

    /**
     * 事务重试最大时间（毫秒）
     * 生产环境建议: 15-30秒
     */
    private long maxTransactionRetryTimeMillis = 15 * 1000L; // 15秒

    /**
     * 默认会话超时时间（毫秒）
     */
    private long sessionTimeoutMillis = 30 * 1000L; // 30秒

    /**
     * 默认事务超时时间（毫秒）
     */
    private long transactionTimeoutMillis = 60 * 1000L; // 60秒

    /**
     * 是否启用自动提交
     */
    private boolean autoCommitEnabled = false;

    /**
     * 是否启用读写分离
     * 集群环境建议: true
     */
    private boolean readWriteSeparationEnabled = false;

    // ==================== 查询和性能配置 ====================

    /**
     * 默认查询超时时间（毫秒）
     * 生产环境建议: 30-60秒
     */
    private long queryTimeoutMillis = 30 * 1000L; // 30秒

    /**
     * 最大查询结果大小
     * 防止内存溢出
     */
    private int maxQueryResultSize = 10000;

    /**
     * 是否启用查询缓存
     */
    private boolean queryCacheEnabled = true;

    /**
     * 查询缓存大小
     */
    private int queryCacheSize = 1000;

    /**
     * 查询缓存过期时间（毫秒）
     */
    private long queryCacheExpireMillis = 5 * 60 * 1000L; // 5分钟

    /**
     * 是否启用查询计划缓存
     */
    private boolean queryPlanCacheEnabled = true;

    // ==================== 监控和日志配置 ====================

    /**
     * 应用名称（用于监控和调试）
     */
    private String applicationName = "richie-vector-neo4j";

    /**
     * 是否启用连接池监控
     * 生产环境建议: true
     */
    private boolean connectionPoolMonitoringEnabled = true;

    /**
     * 连接池监控间隔（毫秒）
     */
    private long connectionPoolMonitoringIntervalMillis = 60 * 1000L; // 1分钟

    /**
     * 是否启用查询日志
     * 生产环境建议: false，避免性能影响
     */
    private boolean queryLoggingEnabled = false;

    /**
     * 查询日志级别
     * 可选值: DEBUG, INFO, WARN, ERROR
     */
    private String queryLoggingLevel = "INFO";

    /**
     * 是否启用性能监控
     * 生产环境建议: true
     */
    private boolean performanceMonitoringEnabled = true;

    /**
     * 性能监控采样率（0.0-1.0）
     * 生产环境建议: 0.1-0.5，平衡监控和性能
     */
    private double performanceMonitoringSamplingRate = 0.1;

    /**
     * 是否启用连接泄漏检测
     * 生产环境建议: true
     */
    private boolean connectionLeakDetectionEnabled = true;

    /**
     * 连接泄漏检测阈值（毫秒）
     * 连接超过此时间未释放将被标记为泄漏
     */
    private long connectionLeakDetectionThresholdMillis = 60 * 1000L; // 1分钟

    // ==================== 健康检查和诊断配置 ====================

    /**
     * 是否启用健康检查
     * 生产环境建议: true
     */
    private boolean healthCheckEnabled = true;

    /**
     * 健康检查间隔（毫秒）
     */
    private long healthCheckIntervalMillis = 30 * 1000L; // 30秒

    /**
     * 健康检查超时时间（毫秒）
     */
    private long healthCheckTimeoutMillis = 5 * 1000L; // 5秒

    /**
     * 健康检查查询
     */
    private String healthCheckQuery = "RETURN 1 as health";

    /**
     * 是否启用诊断信息收集
     */
    private boolean diagnosticEnabled = false;

    /**
     * 诊断信息收集间隔（毫秒）
     */
    private long diagnosticIntervalMillis = 5 * 60 * 1000L; // 5分钟

    // ==================== 高级配置 ====================

    /**
     * 是否启用连接池预热
     * 生产环境建议: true，减少冷启动时间
     */
    private boolean connectionPoolWarmupEnabled = true;

    /**
     * 连接池预热连接数
     */
    private int connectionPoolWarmupSize = 5;

    /**
     * 是否启用连接池统计信息
     */
    private boolean connectionPoolStatsEnabled = true;

    /**
     * 是否启用网络事件监听
     */
    private boolean networkEventListeningEnabled = false;

    /**
     * 是否启用协议协商
     */
    private boolean protocolNegotiationEnabled = true;

    /**
     * 是否启用连接压缩
     */
    private boolean connectionCompressionEnabled = true;

    /**
     * 连接压缩级别（1-9）
     */
    private int connectionCompressionLevel = 6;

    /**
     * 是否启用连接复用
     */
    private boolean connectionReuseEnabled = true;

    /**
     * 连接复用超时时间（毫秒）
     */
    private long connectionReuseTimeoutMillis = 300 * 1000L; // 5分钟
}
