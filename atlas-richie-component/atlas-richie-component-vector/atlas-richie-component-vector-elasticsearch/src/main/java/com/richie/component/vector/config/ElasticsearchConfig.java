package com.richie.component.vector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量数据库配置属性
 * 用于配置不同向量数据库的连接参数和索引设置
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.elasticsearch")
public class ElasticsearchConfig {

    /**
     * Elasticsearch集群地址
     */
    private String clusterUrl = "http://localhost:9200";

    private int connectTimeout = 5000;

    private int socketTimeout = 30000;

    private boolean contentCompressionEnabled = true;

}