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
package com.richie.component.ai.config.resilience;

import lombok.Data;

/**
 * 调用韧性配置(熔断 / 重试等)— 映射 {@code platform.component.ai.resilience}。
 *
 * @author richie696
 */
@Data
public class ResilienceConfig {

    /** 是否启用简易熔断(连续失败后临时跳过该模型)。 */
    private boolean circuitBreakerEnabled = true;

    /** 触发熔断的连续失败次数。 */
    private int failureThreshold = 3;

    /** 熔断打开时长(毫秒)。 */
    private long openDurationMs = 60_000L;
}