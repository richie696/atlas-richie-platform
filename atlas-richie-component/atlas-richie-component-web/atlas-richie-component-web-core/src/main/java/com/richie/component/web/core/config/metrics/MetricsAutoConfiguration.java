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
package com.richie.component.web.core.config.metrics;

import com.richie.component.web.core.metrics.WebMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 指标门面装配（README.md §4.1 / §4.2 / §4.4 / §4.8）。
 * <p>
 * 注册 {@link WebMetrics} 单例供所有 web 拦截器 / SSE / Hang 等组件注入。
 * {@link MeterRegistry} 由 Spring Boot Actuator 自动装配；未引入 Actuator 时 {@code registry=null}，
 * {@link WebMetrics} 自动降级为空操作（<strong>零侵入</strong>）。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@ConditionalOnClass(WebMetrics.class)
public class MetricsAutoConfiguration {

    /**
     * 注册 {@link WebMetrics} 单例。{@link MeterRegistry} 使用 {@link ObjectProvider} 软依赖——未引入 Actuator 时
     * 内部 {@code registry=null}，所有 record* 方法空操作。
     */
    @Bean
    @ConditionalOnMissingBean
    public WebMetrics webMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        return new WebMetrics(registry);
    }
}