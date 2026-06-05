package com.richie.component.threadpool.aysnc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    @Test
    void decorate_runsWrappedTaskWhenDataSourcePropagationDisabled() {
        CustomAsyncThreadPoolProperties properties = new CustomAsyncThreadPoolProperties();
        properties.setEnableDynamicDataSourceContextHolder(false);
        MdcTaskDecorator decorator = new MdcTaskDecorator(properties);
        AtomicBoolean executed = new AtomicBoolean(false);

        Runnable wrapped = decorator.decorate(() -> executed.set(true));
        wrapped.run();

        assertThat(executed).isTrue();
    }

    @Test
    void decorate_swallowsTaskExceptionsWithoutRethrowing() {
        CustomAsyncThreadPoolProperties properties = new CustomAsyncThreadPoolProperties();
        MdcTaskDecorator decorator = new MdcTaskDecorator(properties);

        Runnable wrapped = decorator.decorate(() -> {
            throw new IllegalStateException("task failed");
        });

        wrapped.run();
    }

    @Test
    void properties_defaultValues_matchDocumentation() {
        CustomAsyncThreadPoolProperties properties = new CustomAsyncThreadPoolProperties();

        assertThat(properties.getThreadNamePrefix()).isEqualTo("customtl-");
        assertThat(properties.getQueueCapacity()).isEqualTo(2000);
        assertThat(properties.isEnableDynamicDataSourceContextHolder()).isFalse();
    }
}
