package com.richie.component.threadpool.aysnc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link CustomAsyncThreadPoolAutoConfiguration}.
 */
class CustomAsyncThreadPoolAutoConfigurationTest {

    @Test
    void customAsyncExecutorBean_whenEnabled_shouldBePresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CustomAsyncThreadPoolAutoConfiguration.class))
                .withPropertyValues("platform.thread-pool.enable=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolTaskExecutor.class);
                    assertThat(context).hasSingleBean(MdcTaskDecorator.class);
                });
    }

    @Test
    void customAsyncExecutorBean_whenDisabled_shouldNotBePresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CustomAsyncThreadPoolAutoConfiguration.class))
                .withPropertyValues("platform.thread-pool.enable=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutor.class);
                    assertThat(context).doesNotHaveBean(MdcTaskDecorator.class);
                });
    }

    @Test
    void customAsyncConfigurerBean_whenOverrideEnabled_shouldBePresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CustomAsyncThreadPoolAutoConfiguration.class))
                .withPropertyValues(
                        "platform.thread-pool.enable=true",
                        "platform.thread-pool.override-default-async=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(CustomAsyncConfigurer.class);
                });
    }

    @Test
    void customAsyncConfigurerBean_whenOverrideDisabled_shouldNotBePresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CustomAsyncThreadPoolAutoConfiguration.class))
                .withPropertyValues(
                        "platform.thread-pool.enable=true",
                        "platform.thread-pool.override-default-async=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CustomAsyncConfigurer.class);
                });
    }

    @Test
    void mdcTaskDecorator_shouldBeConditionalOnEnableTaskDecorator() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CustomAsyncThreadPoolAutoConfiguration.class))
                .withPropertyValues(
                        "platform.thread-pool.enable=true",
                        "platform.thread-pool.enable-task-decorator=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MdcTaskDecorator.class);
                    ThreadPoolTaskExecutor executor = context.getBean(ThreadPoolTaskExecutor.class);
                });
    }
}
