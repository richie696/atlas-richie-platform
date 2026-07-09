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
package com.richie.component.ai.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 模型健康检查结果
 */
@Data
@Accessors(chain = true)
public class AiHealthResult {

    private String modelName;

    private String provider;

    private boolean healthy;

    private boolean liveProbe;

    private Long durationMs;

    private String message;

    private LocalDateTime checkedAt;

    public static AiHealthResult healthy(String modelName, String provider, boolean liveProbe, long durationMs) {
        return new AiHealthResult()
                .setModelName(modelName)
                .setProvider(provider)
                .setHealthy(true)
                .setLiveProbe(liveProbe)
                .setDurationMs(durationMs)
                .setMessage("OK")
                .setCheckedAt(LocalDateTime.now());
    }

    public static AiHealthResult unhealthy(String modelName, String provider, boolean liveProbe, String message) {
        return new AiHealthResult()
                .setModelName(modelName)
                .setProvider(provider)
                .setHealthy(false)
                .setLiveProbe(liveProbe)
                .setMessage(message)
                .setCheckedAt(LocalDateTime.now());
    }
}
