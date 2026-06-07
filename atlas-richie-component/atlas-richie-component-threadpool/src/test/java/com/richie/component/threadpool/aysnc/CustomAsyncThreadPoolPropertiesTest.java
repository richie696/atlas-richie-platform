package com.richie.component.threadpool.aysnc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CustomAsyncThreadPoolProperties} defaults and setters.
 * Verifies configuration binding defaults match documented values.
 */
class CustomAsyncThreadPoolPropertiesTest {

    @Test
    void defaults_shouldMatchDocumentation() {
        CustomAsyncThreadPoolProperties props = new CustomAsyncThreadPoolProperties();
        assertThat(props.getThreadNamePrefix()).isEqualTo("customtl-");
        assertThat(props.getQueueCapacity()).isEqualTo(2000);
        assertThat(props.getKeepAliveSeconds()).isEqualTo(60);
        assertThat(props.getAwaitTerminationSeconds()).isEqualTo(120);
        assertThat(props.isEnable()).isFalse();
        assertThat(props.isAllowCoreThreadTimeOut()).isFalse();
        assertThat(props.isWaitForTasksToCompleteOnShutdown()).isFalse();
        assertThat(props.isOverrideDefaultAsync()).isFalse();
        assertThat(props.isEnableTaskDecorator()).isFalse();
        assertThat(props.isEnableDynamicDataSourceContextHolder()).isFalse();
    }

    @Test
    void corePoolSize_and_maxPoolSize_shouldScaleWithCpuCores() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        CustomAsyncThreadPoolProperties props = new CustomAsyncThreadPoolProperties();
        assertThat(props.getCorePoolSize()).isEqualTo(availableProcessors * 2);
        assertThat(props.getMaxPoolSize()).isEqualTo(availableProcessors * 4);
    }

    @Test
    void setters_shouldUpdateValues() {
        CustomAsyncThreadPoolProperties props = new CustomAsyncThreadPoolProperties();
        props.setEnable(true);
        props.setCorePoolSize(8);
        props.setMaxPoolSize(16);
        props.setQueueCapacity(1000);
        props.setThreadNamePrefix("test-");
        props.setKeepAliveSeconds(30);
        props.setAllowCoreThreadTimeOut(true);
        props.setWaitForTasksToCompleteOnShutdown(true);
        props.setAwaitTerminationSeconds(60);
        props.setOverrideDefaultAsync(true);
        props.setEnableTaskDecorator(true);
        props.setEnableDynamicDataSourceContextHolder(true);

        assertThat(props.isEnable()).isTrue();
        assertThat(props.getCorePoolSize()).isEqualTo(8);
        assertThat(props.getMaxPoolSize()).isEqualTo(16);
        assertThat(props.getQueueCapacity()).isEqualTo(1000);
        assertThat(props.getThreadNamePrefix()).isEqualTo("test-");
        assertThat(props.getKeepAliveSeconds()).isEqualTo(30);
        assertThat(props.isAllowCoreThreadTimeOut()).isTrue();
        assertThat(props.isWaitForTasksToCompleteOnShutdown()).isTrue();
        assertThat(props.getAwaitTerminationSeconds()).isEqualTo(60);
        assertThat(props.isOverrideDefaultAsync()).isTrue();
        assertThat(props.isEnableTaskDecorator()).isTrue();
        assertThat(props.isEnableDynamicDataSourceContextHolder()).isTrue();
    }
}
