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
