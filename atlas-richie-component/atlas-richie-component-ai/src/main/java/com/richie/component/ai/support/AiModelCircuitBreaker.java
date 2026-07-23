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
package com.richie.component.ai.support;

import com.richie.component.ai.config.resilience.ResilienceConfig;

import com.richie.component.ai.config.chat.AiChatModel;

import com.richie.component.ai.config.AiModelProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按模型维度的简易熔断器（内存级，适用于单实例网关/服务）。
 */
@Component
public class AiModelCircuitBreaker {

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public boolean allow(String modelName, ResilienceConfig config) {
        if (!config.isCircuitBreakerEnabled()) {
            return true;
        }
        State state = states.get(modelName);
        if (state == null || !state.open) {
            return true;
        }
        if (System.currentTimeMillis() >= state.openUntilMs) {
            states.remove(modelName);
            return true;
        }
        return false;
    }

    public void recordSuccess(String modelName) {
        states.remove(modelName);
    }

    public void recordFailure(String modelName, ResilienceConfig config) {
        if (!config.isCircuitBreakerEnabled()) {
            return;
        }
        State state = states.computeIfAbsent(modelName, ignored -> new State());
        state.consecutiveFailures++;
        if (state.consecutiveFailures >= config.getFailureThreshold()) {
            state.open = true;
            state.openUntilMs = System.currentTimeMillis() + config.getOpenDurationMs();
        }
    }

    public boolean isOpen(String modelName) {
        State state = states.get(modelName);
        return state != null && state.open && System.currentTimeMillis() < state.openUntilMs;
    }

    private static final class State {
        private int consecutiveFailures;
        private boolean open;
        private long openUntilMs;
    }
}
