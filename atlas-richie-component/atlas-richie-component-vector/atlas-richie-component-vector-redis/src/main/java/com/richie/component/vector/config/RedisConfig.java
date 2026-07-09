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
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.redis")
public class RedisConfig {

    private String clientName = "richie-vector-client";
    /**
     * Redis主机
     */
    private String host = "localhost";

    /**
     * Redis端口
     */
    private int port = 6379;

    /**
     * Redis用户名（可选，适用于Redis 6及以上）
     */
    private String username;

    /**
     * Redis密码
     */
    private String password;

    /**
     * 数据库索引
     */
    private int database = 0;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectionTimeout = 2000;

    /**
     * 读取超时时间（毫秒）
     */
    private int socketTimeout = 2000;

    /**
     * 阻塞套接字超时时间（毫秒）
     */
    private int blockingSocketTimeout = 2000;

}
