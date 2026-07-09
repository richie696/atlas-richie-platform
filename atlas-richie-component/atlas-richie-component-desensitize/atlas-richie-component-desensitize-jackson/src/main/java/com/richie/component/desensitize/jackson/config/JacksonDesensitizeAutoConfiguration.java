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
package com.richie.component.desensitize.jackson.config;

import com.richie.context.utils.data.JsonUtilsModuleCustomizer;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.jackson.DesensitizeJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JacksonModule;

import java.util.List;

/**
 * Jackson 脱敏自动配置。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@AutoConfiguration(after = com.richie.component.desensitize.core.config.DesensitizeAutoConfiguration.class)
@ConditionalOnClass(JacksonModule.class)
@ConditionalOnBean(MaskingService.class)
public class JacksonDesensitizeAutoConfiguration {

    /**
     * 注册 Jackson 脱敏模块。
     *
     * @param maskingService 脱敏服务
     * @param sensitiveKeyRegistry 敏感键注册表
     * @return Jackson 模块
     */
    @Bean
    public JacksonModule desensitizeJacksonModule(MaskingService maskingService, SensitiveKeyRegistry sensitiveKeyRegistry) {
        return new DesensitizeJacksonModule(maskingService, sensitiveKeyRegistry);
    }

    /**
     * 将脱敏模块注入 JsonUtils 的模块定制链。
     *
     * @param desensitizeJacksonModule 脱敏模块
     * @return JsonUtils 模块定制器
     */
    @Bean
    public JsonUtilsModuleCustomizer desensitizeJsonUtilsModuleCustomizer(JacksonModule desensitizeJacksonModule) {
        return () -> List.of(desensitizeJacksonModule);
    }
}
