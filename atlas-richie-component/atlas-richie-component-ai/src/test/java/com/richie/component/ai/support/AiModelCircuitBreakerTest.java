package com.richie.component.ai.support;

import com.richie.component.ai.config.AiModelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiModelCircuitBreakerTest {

    private final AiModelCircuitBreaker circuitBreaker = new AiModelCircuitBreaker();

    @Test
    void shouldOpenAfterThresholdFailures() {
        AiModelProperties.ResilienceConfig config = new AiModelProperties.ResilienceConfig();
        config.setFailureThreshold(2);
        config.setOpenDurationMs(60_000);

        assertTrue(circuitBreaker.allow("gpt-4o", config));
        circuitBreaker.recordFailure("gpt-4o", config);
        assertTrue(circuitBreaker.allow("gpt-4o", config));
        circuitBreaker.recordFailure("gpt-4o", config);
        assertFalse(circuitBreaker.allow("gpt-4o", config));
    }

    @Test
    void recordSuccessShouldResetBreaker() {
        AiModelProperties.ResilienceConfig config = new AiModelProperties.ResilienceConfig();
        config.setFailureThreshold(1);

        circuitBreaker.recordFailure("gpt-4o", config);
        assertFalse(circuitBreaker.allow("gpt-4o", config));

        circuitBreaker.recordSuccess("gpt-4o");
        assertTrue(circuitBreaker.allow("gpt-4o", config));
    }
}
