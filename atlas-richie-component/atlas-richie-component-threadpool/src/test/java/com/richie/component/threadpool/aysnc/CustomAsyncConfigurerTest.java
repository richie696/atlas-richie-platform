package com.richie.component.threadpool.aysnc;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link CustomAsyncConfigurer}.
 */
class CustomAsyncConfigurerTest {

    @Test
    void getAsyncExecutor_shouldReturnConfiguredExecutor() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        CustomAsyncConfigurer configurer = new CustomAsyncConfigurer(executor);

        Executor result = configurer.getAsyncExecutor();

        assertThat(result).isSameAs(executor);
    }

    @Test
    void getAsyncUncaughtExceptionHandler_shouldReturnHandler() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        CustomAsyncConfigurer configurer = new CustomAsyncConfigurer(executor);

        AsyncUncaughtExceptionHandler handler = configurer.getAsyncUncaughtExceptionHandler();

        assertThat(handler).isNotNull();
    }

    @Test
    void exceptionHandler_shouldFormatMethodAndParams() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        CustomAsyncConfigurer configurer = new CustomAsyncConfigurer(executor);
        AsyncUncaughtExceptionHandler handler = configurer.getAsyncUncaughtExceptionHandler();

        handler.handleUncaughtException(new RuntimeException("test error"),
                getClass().getMethods()[0],
                new Object[]{"param1", "param2"});
    }
}
