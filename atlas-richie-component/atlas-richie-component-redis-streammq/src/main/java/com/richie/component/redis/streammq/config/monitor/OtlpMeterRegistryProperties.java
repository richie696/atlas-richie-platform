/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.redis.streammq.config.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTLP MeterRegistry 配置属性
 *
 * <p>用于配置 OTLP 指标导出的相关参数。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-01-29
 */
@Data
@ConfigurationProperties(prefix = "management.otlp.metrics")
public class OtlpMeterRegistryProperties {

    /**
     * 是否启用 OTLP 指标导出
     */
    private boolean enabled = false;

    /**
     * OTLP 服务端点
     */
    private String endpoint;

    /**
     * 服务名称
     */
    private String serviceName = "redis-stream-service";

    /**
     * 超时时间（秒）
     */
    private int timeoutSeconds = 30;
}
