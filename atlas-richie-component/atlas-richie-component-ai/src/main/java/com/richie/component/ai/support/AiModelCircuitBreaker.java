package com.richie.component.ai.support;

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

    public boolean allow(String modelName, AiModelProperties.ResilienceConfig config) {
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

    public void recordFailure(String modelName, AiModelProperties.ResilienceConfig config) {
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
