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
package com.richie.component.mongodb.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.ServerListener;
import com.mongodb.event.ServerMonitorListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <b>MongoDB 自动配置详解：</b>
 * <p>
 * 本配置类用于生产环境下 MongoDB Client 的自动化初始化，具备如下特性：
 * <ul>
 *   <li><b>认证与连接参数：</b> 支持用户名、密码、认证数据库配置，主机/端口/连接池/超时/心跳/监控模式等全量参数，适配企业级需求。</li>
 *   <li><b>SSL/TLS 安全连接：</b> 支持 SSL 启用、主机名校验、JKS 证书加载、自定义 SSLContext，兼容云厂商与自建环境。</li>
 *   <li><b>监控与扩展：</b> 支持自定义 ServerListener、ServerMonitorListener，便于监控与事件扩展。</li>
 *   <li><b>Spring 集成：</b> 提供 MongoTemplate Bean，便于与 Spring Data MongoDB 生态无缝集成。</li>
 *   <li><b>异常处理与健壮性：</b> SSLContext 初始化异常抛出 RuntimeException，参数异常在 Spring 启动时暴露，便于排查。</li>
 * </ul>
 * <b>生产环境建议补充项：</b>
 * <ul>
 *   <li>支持多节点/副本集/分片集群（建议优先用 MongoDB URI，自动适配高可用）。</li>
 *   <li>连接池健康检查、超时重试（如 heartbeatConnectTimeoutMS、serverSelectionTimeoutMS）。</li>
 *   <li>读写分离/优先级（如 readPreference、writeConcern）。</li>
 *   <li>敏感信息加密（如密码、证书路径建议用环境变量或加密配置）。</li>
 *   <li>日志与审计（可集成 CommandListener 记录慢查询、异常、审计日志）。</li>
 *   <li>连接池参数动态调整（如需高可用可暴露相关配置）。</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-03 16:20:52
 */
@Configuration
@ComponentScan(basePackages = {"com.richie.component.mongodb"})
@EnableConfigurationProperties(MongodbConfig.class)
public class MongodbAutoConfiguration {

    /**
     * MongoDB 事务管理器 Bean，用于支持 Mongo 事务（需副本集/分片集群）。
     *
     * @param dbFactory Mongo 数据库工厂
     * @return MongoTransactionManager 实例
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /**
     * MongoTemplate Bean自动注入
     * 支持用户名密码认证、连接池、心跳、SSL等高级配置
     *
     * @param config                MongodbConfig 配置参数
     * @param serverListener        自定义ServerListener
     * @param serverMonitorListener 自定义ServerMonitorListener
     * @return MongoTemplate 实例
     */
    @Bean
    public MongoClient mongoClient(MongodbConfig config,
                                   ServerListener serverListener,
                                   ServerMonitorListener serverMonitorListener) {
        // 支持用户名密码认证
        MongoClient mongoClient;
        if (config.getUsername() != null && config.getPassword() != null) {
            // 构建认证信息，指定认证数据库
            var credential = MongoCredential.createCredential(
                    config.getUsername(), // 用户名
                    config.getAuthDatabase() != null ? config.getAuthDatabase() : config.getDatabase(), // 认证数据库
                    config.getPassword().toCharArray() // 密码
            );
            // 构建MongoClientSettings
            var settingsBuilder = MongoClientSettings.builder();

            // 优先支持URI配置，便于副本集/分片/认证等高级参数自动适配
            if (config.getUri() != null && !config.getUri().isEmpty()) {
                // 推荐生产环境优先用URI，支持多节点/副本集/分片/认证等所有高级参数
                settingsBuilder.applyConnectionString(new ConnectionString(config.getUri()));
            } else {
                // 设置集群主机和端口
                settingsBuilder.applyToClusterSettings(builder -> builder.hosts(
                        Collections.singletonList(
                                new ServerAddress(config.getHost(), config.getPort()) // 主机和端口
                        )
                ));
            }
            settingsBuilder
                    .credential(credential) // 设置认证
                    // 连接池相关设置
                    .applyToConnectionPoolSettings(builder -> builder
                            .maxConnecting(config.getMaxConnecting()) // 最大连接数
                            .minSize(config.getMinConnectionPoolSize()) // 最小连接池
                            .maxSize(config.getMaxConnectionPoolSize()) // 最大连接池
                            .maxWaitTime(config.getMaxWaitTimeMs(), TimeUnit.MILLISECONDS) // 最大等待时间
                            .maxConnectionIdleTime(config.getConnectionIdleTime(), TimeUnit.MILLISECONDS)) // 最大空闲时间
                    // Socket超时设置
                    .applyToSocketSettings(builder -> builder
                            .readTimeout(config.getSocketTimeoutMs(), TimeUnit.MILLISECONDS) // 读超时
                            .connectTimeout(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)) // 连接超时
                    // 服务器监控与心跳设置
                    .applyToServerSettings(builder -> builder
                            .heartbeatFrequency(config.getHeartbeatFrequency(), TimeUnit.MILLISECONDS) // 心跳频率
                            .minHeartbeatFrequency(config.getMinHeartbeatFrequency(), TimeUnit.MILLISECONDS) // 最小心跳频率
                            .serverMonitoringMode(config.getServerMonitoringMode()) // 监控模式
                            // 监听器扩展点：优先用业务方Bean，否则用默认实现
                            .addServerListener(serverListener)
                            .addServerMonitorListener(serverMonitorListener));
            // SSL安全连接配置
            if (config.isSslEnabled()) {
                settingsBuilder.applyToSslSettings(builder -> builder
                        .enabled(true) // 启用SSL
                        .invalidHostNameAllowed(config.isInvalidHostNameAllowed()) // 是否允许主机名不匹配
                        .context(createSSLContext(config)) // 自定义SSLContext
                );
            }
            mongoClient = MongoClients.create(settingsBuilder.build()); // 创建MongoClient
        } else {
            // 无认证直连（适用于本地开发或无权限环境）
            mongoClient = MongoClients.create(); // 默认本地连接
        }
        return mongoClient;
    }

    /**
     * MongoTemplate Bean实例
     *
     * @param mongoClient MongoClient 实例
     * @param config MongodbConfig 配置参数
     * @return MongoTemplate 实例
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoClient mongoClient, MongodbConfig config) {
        // 返回MongoTemplate实例，指定数据库
        return new MongoTemplate(mongoClient, config.getDatabase());
    }

    /**
     * 构建SSLContext，支持JKS证书或默认信任管理器
     *
     * @param config MongodbConfig 配置参数
     * @return SSLContext 实例
     */
    private SSLContext createSSLContext(MongodbConfig config) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS"); // 使用TLS协议
            // 如果有证书文件路径配置，加载JKS证书
            if (config.getSslCertificatePath() != null) {
                KeyStore keyStore = KeyStore.getInstance("JKS"); // JKS密钥库
                try (FileInputStream fis = new FileInputStream(config.getSslCertificatePath())) {
                    keyStore.load(fis, config.getSslCertificatePassword().toCharArray()); // 加载证书和密码
                }

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); // 默认信任管理器
                tmf.init(keyStore); // 初始化信任管理器

                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom()); // 初始化SSLContext
            } else {
                // 使用默认信任管理器（适合大多数云环境）
                sslContext.init(null, null, null);
            }

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e); // 抛出异常便于排查
        }
    }

}
