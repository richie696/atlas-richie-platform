package com.richie.component.nats.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultNatsErrorStrategy} 单元测试
 */
class DefaultNatsErrorStrategyTest {

    private DefaultNatsErrorStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultNatsErrorStrategy();
    }

    @Test
    void shouldRetry_shouldReturnTrue_whenAttemptsRemain() {
        boolean result = strategy.shouldRetry(new IOException("network error"), 1, 3);
        assertThat(result).isTrue();
    }

    @Test
    void shouldRetry_shouldReturnFalse_whenMaxAttemptsReached() {
        boolean result = strategy.shouldRetry(new IOException("network error"), 3, 3);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRetry_shouldReturnFalse_whenAttemptExceedsMax() {
        boolean result = strategy.shouldRetry(new IOException("network error"), 5, 3);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRetry_shouldReturnFalse_forInterruptedException() {
        boolean result = strategy.shouldRetry(new InterruptedException("interrupted"), 1, 3);
        assertThat(result).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear the interrupt flag
        Thread.interrupted();
    }

    @Test
    void onPublishError_shouldNotThrow() {
        strategy.onPublishError("test.subject", "hello".getBytes(), new RuntimeException("test"));
        // No exception expected - just logging
    }

    @Test
    void onConsumeError_shouldNotThrow() {
        strategy.onConsumeError("test.subject", null, new RuntimeException("test"));
        // No exception expected - just logging
    }

    @Test
    void shouldRetry_withRuntimeException_shouldReturnTrue() {
        boolean result = strategy.shouldRetry(new RuntimeException("unexpected"), 1, 5);
        assertThat(result).isTrue();
    }
}
