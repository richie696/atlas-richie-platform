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
package com.richie.component.messaging.pulsar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.pulsar.properties.PulsarBinderConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * Pulsar Binder 配置属性。
 * <p>
 * 配置前缀：{@code spring.cloud.stream.pulsar.binder}，继承 Spring Cloud Stream Pulsar 默认配置。
 *
 * @author richie696
 * @since 2022-09-27
 */
@Primary
@ConfigurationProperties(prefix = "spring.cloud.stream.pulsar.binder")
public class PulsarBinderProperties extends PulsarBinderConfigurationProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public PulsarBinderProperties() {
    }
}
