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
package com.richie.context.utils.data;

import tools.jackson.databind.JacksonModule;

import java.util.List;

/**
 * JsonUtils Jackson Module 扩展点。
 * <p>
 * 各组件可通过 Spring Bean 提供实现，由自动配置统一收集并注册到 {@link JsonUtils}。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@FunctionalInterface
public interface JsonUtilsModuleCustomizer {

    /**
     * 提供需要注册到 JsonUtils 的 Jackson 模块。
     *
     * @return 模块列表
     */
    List<JacksonModule> modules();
}

