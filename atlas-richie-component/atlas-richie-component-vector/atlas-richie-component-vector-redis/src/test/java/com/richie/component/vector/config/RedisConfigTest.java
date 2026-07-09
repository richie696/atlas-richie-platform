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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisConfig} 单元测试
 * <p>
 * 验证所有配置属性的 setter/getter 以及默认值。
 */
class RedisConfigTest {

    @Test
    void defaults_shouldHaveCorrectValues() {
        RedisConfig config = new RedisConfig();

        assertThat(config.getClientName()).isEqualTo("richie-vector-client");
        assertThat(config.getHost()).isEqualTo("localhost");
        assertThat(config.getPort()).isEqualTo(6379);
        assertThat(config.getDatabase()).isEqualTo(0);
        assertThat(config.getConnectionTimeout()).isEqualTo(2000);
        assertThat(config.getSocketTimeout()).isEqualTo(2000);
        assertThat(config.getBlockingSocketTimeout()).isEqualTo(2000);
        assertThat(config.getUsername()).isNull();
        assertThat(config.getPassword()).isNull();
    }

    @Test
    void setters_shouldUpdateValues() {
        RedisConfig config = new RedisConfig();

        config.setClientName("custom-client");
        config.setHost("redis.example.com");
        config.setPort(6380);
        config.setUsername("admin");
        config.setPassword("secret");
        config.setDatabase(1);
        config.setConnectionTimeout(5000);
        config.setSocketTimeout(3000);
        config.setBlockingSocketTimeout(4000);

        assertThat(config.getClientName()).isEqualTo("custom-client");
        assertThat(config.getHost()).isEqualTo("redis.example.com");
        assertThat(config.getPort()).isEqualTo(6380);
        assertThat(config.getUsername()).isEqualTo("admin");
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.getDatabase()).isEqualTo(1);
        assertThat(config.getConnectionTimeout()).isEqualTo(5000);
        assertThat(config.getSocketTimeout()).isEqualTo(3000);
        assertThat(config.getBlockingSocketTimeout()).isEqualTo(4000);
    }
}
