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

import com.richie.component.vector.operations.VectorOperationsFacade;
import com.richie.component.vector.service.VectorService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 向量数据库自动配置类。
 *
 * <p>注册以下 Bean：</p>
 * <ul>
 *   <li>{@link VectorProperties} — 主配置属性</li>
 *   <li>{@link VectorFacadeProperties} — 跨 provider 调度配置</li>
 *   <li>{@link VectorOperationsFacade} — 跨 provider 调度门面（含重试/回退/指标），仅当至少 1 个 {@link VectorService} provider 就绪时创建</li>
 * </ul>
 */
@Configuration
@ComponentScan(basePackages = {"com.richie.component.vector"})
@EnableConfigurationProperties({VectorProperties.class, VectorFacadeProperties.class})
public class VectorAutoConfiguration {

    /**
     * 跨 provider 调度门面。
     * <p>
     * Spring 自动注入所有 {@link VectorService} 实现（按 bean name 索引）；
     * {@link MeterRegistry} 可选 — 当业务侧没有 Micrometer 时，门面仍可工作（仅跳过指标记录）。
     * <p>
     * <b>触发条件</b>：仅当容器中已注册至少 1 个 {@link VectorService} bean 时才创建 —
     * 各 provider impl 的 {@code @ConditionalOnProperty(provider=xxx)} 在应用未配置
     * {@code platform.component.vector.provider} 时不会注册任何 bean,此时避免 facade autowire 失败。
     */
    @Bean
    @ConditionalOnBean(VectorService.class)
    public VectorOperationsFacade vectorOperationsFacade(Map<String, VectorService> providers,
                                                          VectorFacadeProperties props,
                                                          ObjectProvider<MeterRegistry> meterRegistry) {
        return new VectorOperationsFacade(providers, props, meterRegistry);
    }

}
