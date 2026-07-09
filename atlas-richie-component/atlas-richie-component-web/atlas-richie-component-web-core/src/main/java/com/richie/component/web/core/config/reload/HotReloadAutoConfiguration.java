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
package com.richie.component.web.core.config.reload;

import com.richie.component.web.core.hook.HookBus;
import com.richie.component.web.core.reload.DefaultHotReloadRegistry;
import com.richie.component.web.core.reload.HotReloadCloudBridge;
import com.richie.component.web.core.reload.HotReloadRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * HotReload 自动装配（README.md §4.6）。
 * <p>
 * 装配：
 * <ol>
 *   <li>{@link HotReloadRegistry}：默认 {@link DefaultHotReloadRegistry}</li>
 *   <li>{@link HotReloadCloudBridge}：可选，当 classpath 存在
 *       {@code org.springframework.cloud.context.environment.EnvironmentChangeEvent} 时自动激活，
 *       实现 Spring Cloud Config 自动 reload</li>
 * </ol>
 *
 * <h2>不依赖 Spring Cloud</h2>
 * <p>web-core 不引入 spring-cloud 编译期依赖；通过 {@code @ConditionalOnClass(name=...)}
 * 仅在用户已引入 spring-cloud-context 时桥接器才装配。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@ConditionalOnClass(HotReloadRegistry.class)
public class HotReloadAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HotReloadRegistry hotReloadRegistry(ObjectProvider<HookBus> hookBusProvider) {
        HookBus hookBus = hookBusProvider.getIfAvailable();
        return new DefaultHotReloadRegistry(hookBus);
    }

    /**
     * Spring Cloud Config 桥接器（仅在 spring-cloud-context 在 classpath 时装配）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = HotReloadCloudBridge.ENVIRONMENT_CHANGE_EVENT_CLASS)
    public HotReloadCloudBridge hotReloadCloudBridge(HotReloadRegistry registry) {
        return new HotReloadCloudBridge(registry);
    }
}