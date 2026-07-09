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
package com.richie.component.messaging.pulsar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Pulsar 消息组件配置类。
 * <p>
 * 扫描消息组件与 Spring Cloud Stream Pulsar Binder 包，并启用 Pulsar 相关配置属性。
 *
 * @author richie696
 * @version 1.1
 * @since 2020/07/02
 */
@Slf4j
@Configuration
@ComponentScan({
        "com.richie.component.messaging",
        "org.springframework.cloud.stream.binder.pulsar",
})
@EnableConfigurationProperties({PulsarBinderProperties.class})
public class PulsarConfiguration {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public PulsarConfiguration() {
    }
}
