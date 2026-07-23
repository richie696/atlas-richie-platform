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
package com.richie.component.ai.config.routing;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型路由与降级配置 — 映射 {@code platform.component.ai.routing}。
 *
 * @author richie696
 */
@Data
public class RoutingConfig {

    /** 是否启用场景路由与自动降级链。 */
    private boolean enabled = false;

    /** 主模型失败时是否尝试 fallback 链中的后续模型。 */
    private boolean fallbackEnabled = true;

    /** 全局 fallback 模型链(在主模型失败后追加,去重)。 */
    private List<String> fallbackModels = new ArrayList<>();

    /** 按场景(scene)选择模型链,按顺序尝试。 */
    private Map<String, List<String>> sceneRules = new LinkedHashMap<>();
}