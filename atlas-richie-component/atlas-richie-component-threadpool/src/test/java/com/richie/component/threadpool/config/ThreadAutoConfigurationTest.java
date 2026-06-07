package com.richie.component.threadpool.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ThreadAutoConfiguration}.
 */
class ThreadAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ThreadAutoConfiguration.class));

    @Test
    void shouldEnableConfigurationProperties() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TomcatProps.class);
            assertThat(context).hasSingleBean(JettyProps.class);
            assertThat(context).hasSingleBean(UndertowProps.class);
            assertThat(context).hasSingleBean(ZookeeperProps.class);
            assertThat(context).hasSingleBean(EtcdProps.class);
        });
    }
}
