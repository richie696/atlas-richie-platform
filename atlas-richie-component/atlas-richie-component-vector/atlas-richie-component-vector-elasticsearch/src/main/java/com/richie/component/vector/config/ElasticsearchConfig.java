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