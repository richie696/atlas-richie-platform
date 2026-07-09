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
package com.richie.component.nats.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MemoryNatsIdempotentChecker} 单元测试
 */
class MemoryNatsIdempotentCheckerTest {

    private MemoryNatsIdempotentChecker checker;

    @BeforeEach
    void setUp() {
        checker = new MemoryNatsIdempotentChecker();
    }

    @Test
    void isFirstTime_shouldReturnTrue_forNewMessage() {
        assertThat(checker.isFirstTime("msg-001", 60_000L)).isTrue();
    }

    @Test
    void isFirstTime_shouldReturnFalse_forDuplicateMessage() {
        checker.isFirstTime("msg-001", 60_000L);
        assertThat(checker.isFirstTime("msg-001", 60_000L)).isFalse();
    }

    @Test
    void isFirstTime_shouldReturnTrue_forDifferentMessages() {
        assertThat(checker.isFirstTime("msg-001", 60_000L)).isTrue();
        assertThat(checker.isFirstTime("msg-002", 60_000L)).isTrue();
    }

    @Test
    void clear_shouldAllowReProcessingAfterClear() {
        checker.isFirstTime("msg-001", 60_000L);
        assertThat(checker.isFirstTime("msg-001", 60_000L)).isFalse();

        checker.clear("msg-001");
        assertThat(checker.isFirstTime("msg-001", 60_000L)).isTrue();
    }

    @Test
    void isFirstTime_shouldExpireOldEntries() throws InterruptedException {
        long shortTtl = 100L; // 100ms TTL
        checker.isFirstTime("msg-001", shortTtl);

        // Wait for TTL to expire
        Thread.sleep(200L);

        // Should be treated as first time again after TTL expires
        assertThat(checker.isFirstTime("msg-001", shortTtl)).isTrue();
    }

    @Test
    void isFirstTime_shouldNotExpireFreshEntries() throws InterruptedException {
        long longTtl = 60_000L; // 60s TTL
        checker.isFirstTime("msg-001", longTtl);

        Thread.sleep(50L);

        // Should still be considered duplicate within TTL
        assertThat(checker.isFirstTime("msg-001", longTtl)).isFalse();
    }

    @Test
    void clear_nonExistentMessage_shouldNotThrow() {
        checker.clear("non-existent");
        // No exception expected
    }
}
