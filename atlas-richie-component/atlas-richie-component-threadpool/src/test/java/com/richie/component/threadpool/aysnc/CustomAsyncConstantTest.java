package com.richie.component.threadpool.aysnc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CustomAsyncConstant}.
 */
class CustomAsyncConstantTest {

    @Test
    void platformThreadPoolPrefix_shouldMatchExpectedValue() {
        // The prefix must align with what CustomAsyncThreadPoolProperties binds to
        assertThat(CustomAsyncConstant.PLATFORM_THREAD_POOL_PREFIX).isEqualTo("platform.thread-pool");
    }
}
