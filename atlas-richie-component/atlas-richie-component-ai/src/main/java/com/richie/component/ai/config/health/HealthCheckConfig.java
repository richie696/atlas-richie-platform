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
package com.richie.component.ai.config.health;

import lombok.Data;

/**
 * 健康检查配置 — 映射 {@code platform.component.ai.health-check}。
 *
 * @author richie696
 */
@Data
public class HealthCheckConfig {

    /** probe 时是否发起真实 LLM 调用(false 则仅检查 ChatClient 是否存在)。 */
    private boolean liveProbe = true;

    /** live probe 使用的最大输出 token。 */
    private int probeMaxTokens = 1;
}