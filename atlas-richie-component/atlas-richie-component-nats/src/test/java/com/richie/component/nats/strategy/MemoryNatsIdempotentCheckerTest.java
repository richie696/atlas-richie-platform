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
