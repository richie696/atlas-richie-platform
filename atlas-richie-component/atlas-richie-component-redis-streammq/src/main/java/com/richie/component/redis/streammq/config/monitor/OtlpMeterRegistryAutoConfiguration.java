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
package com.richie.component.redis.streammq.config.monitor;

import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OTLP MeterRegistry 自动配置
 *
 * <p>提供条件化的 OTLP MeterRegistry 配置，只有在明确配置了 OTLP 服务地址时才启用。
 *
 * <p><strong>配置属性：</strong>
 * <pre>{@code
 * # 启用 OTLP 指标导出
 * management.otlp.metrics.enabled=true
 *
 * # OTLP 服务地址
 * management.otlp.metrics.endpoint=http://localhost:4318/v1/metrics
 *
 * # 服务名称
 * management.otlp.metrics.service-name=redis-stream-service
 * }</pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-01-29
 */
@Slf4j
@Configuration
@ConditionalOnClass({OtlpMeterRegistry.class, OtlpConfig.class})
@EnableConfigurationProperties(OtlpMeterRegistryProperties.class)
public class OtlpMeterRegistryAutoConfiguration {

    /**
     * 配置 OTLP MeterRegistry
     *
     * <p>只有在明确配置了 OTLP 服务地址时才创建 OtlpMeterRegistry。
     *
     * @param properties OTLP 配置属性
     * @return OtlpMeterRegistry 实例
     */
    @Bean
    @ConditionalOnMissingBean(OtlpMeterRegistry.class)
    @ConditionalOnProperty(
        prefix = "management.otlp.metrics",
        name = "enabled",
        havingValue = "true"
    )
    public OtlpMeterRegistry otlpMeterRegistry(OtlpMeterRegistryProperties properties) {
        // 检查是否配置了有效的端点
        if (properties.getEndpoint() == null || properties.getEndpoint().trim().isEmpty()) {
            log.warn("OTLP 指标导出已启用但未配置端点，跳过 OtlpMeterRegistry 创建");
            return null;
        }

        log.info("配置 OTLP MeterRegistry: endpoint={}, serviceName={}",
                properties.getEndpoint(), properties.getServiceName());

        OtlpConfig otlpConfig = key -> switch (key) {
            case "otlp.url" -> properties.getEndpoint();
            case "otlp.resource-attributes" -> "service.name=%s".formatted(properties.getServiceName());
            case "otlp.timeout" -> "%ds".formatted(properties.getTimeoutSeconds());
            default -> null;
        };

        return OtlpMeterRegistry.builder(otlpConfig).build();
    }
}
