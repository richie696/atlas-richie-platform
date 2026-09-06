/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorQueryExecutionGuardTest {

    @Test
    void enforcesCallerVisibleTimeoutWithStableCode() {
        VectorQueryExecutionGuard guard = new VectorQueryExecutionGuard(1);

        assertThatThrownBy(() -> guard.execute(Duration.ofMillis(20), () -> {
            try {
                Thread.sleep(5_000L);
                return "late";
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        })).isInstanceOfSatisfying(VectorQueryValidationException.class,
                error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.TIMEOUT));
    }

    @Test
    void rejectsExcessConcurrentCallsWithoutQueueingUnboundedWork() throws Exception {
        VectorQueryExecutionGuard guard = new VectorQueryExecutionGuard(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = new Thread(() -> guard.execute(Duration.ofSeconds(2), () -> {
            entered.countDown();
            try {
                release.await(1, TimeUnit.SECONDS);
                return "done";
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        }));
        first.start();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> guard.execute(Duration.ofSeconds(1), () -> "second"))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.CONCURRENCY_LIMIT));
        release.countDown();
        first.join(2_000L);
        assertThat(first.isAlive()).isFalse();
    }
}
