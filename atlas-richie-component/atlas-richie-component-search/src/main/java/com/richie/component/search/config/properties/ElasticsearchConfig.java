package com.richie.component.search.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch 特有配置类
 *
 * <p>包含 Elasticsearch 特有的功能配置。
 * <p>通用配置（连接地址、认证、超时等）请使用 {@link SearchProperties} 的顶层属性。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Data
@ConfigurationProperties(prefix = "platform.component.search.elasticsearch")
public class ElasticsearchConfig {

    /**
     * 默认分片数
     *
     * <p>创建索引时的默认主分片数量。
     * <p>默认值：1
     */
    private Integer shards = 1;

    /**
     * 默认副本数
     *
     * <p>每个主分片的副本数量。
     * <p>默认值：1
     */
    private Integer replicas = 1;

    /**
     * 刷新间隔
     *
     * <p>索引刷新的时间间隔。
     * <p>默认值：1s
     */
    private String refreshInterval = "1s";

    /**
     * 集群配置
     *
     * <p>Elasticsearch 集群相关配置。
     */
    private ClusterConfig cluster = new ClusterConfig();

    /**
     * 集群配置类
     *
     * <p>Elasticsearch 集群健康检查和验证相关配置。
     *
     * @author richie696
     * @version 1.0
     * @since 2025-08-08
     */
    @Data
    public static class ClusterConfig {

        /**
         * 是否启用健康检查
         *
         * <p>启动时是否检查集群健康状态。
         * <p>默认值：true
         */
        private boolean healthCheck = true;

        /**
         * 健康检查超时时间
         *
         * <p>等待集群健康状态的超时时间。
         * <p>单位：毫秒，默认值：30000
         */
        private Integer healthCheckTimeout = 30000;

        /**
         * 期望的集群名称
         *
         * <p>用于验证连接的集群名称是否正确。
         * <p>如果为空，则不进行集群名称验证。
         */
        private String name;

        /**
         * 最小主节点数
         *
         * <p>集群正常运行所需的最小主节点数。
         * <p>默认值：1
         */
        private Integer minimumMasterNodes = 1;
    }
}
