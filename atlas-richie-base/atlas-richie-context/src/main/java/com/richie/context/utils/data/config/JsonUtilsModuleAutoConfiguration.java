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
package com.richie.context.utils.data.config;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.data.JsonUtilsModuleCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JacksonModule;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动收集并注册 JsonUtils 扩展模块。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@AutoConfiguration
public class JsonUtilsModuleAutoConfiguration {

    @Bean
    public SmartInitializingSingleton jsonUtilsModuleRegistrar(
            ObjectProvider<List<JsonUtilsModuleCustomizer>> customizersProvider) {
        return () -> {
            List<JsonUtilsModuleCustomizer> customizers = customizersProvider.getIfAvailable(List::of);
            if (customizers.isEmpty()) {
                return;
            }
            List<JacksonModule> modules = new ArrayList<>();
            for (JsonUtilsModuleCustomizer customizer : customizers) {
                if (customizer == null || customizer.modules() == null) {
                    continue;
                }
                modules.addAll(customizer.modules());
            }
            JsonUtils.registerGlobalModules(modules);
        };
    }
}

