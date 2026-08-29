/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SecretObservabilityAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecretObservabilityAutoConfiguration.class));

    @Test
    void disabledDoesNotRegisterObservabilityBeans() {
        contextRunner.withUserConfiguration(DiagnosticsConfiguration.class).run(context -> {
            assertThat(context).doesNotHaveBean(HealthIndicator.class);
            assertThat(context).doesNotHaveBean(MeterBinder.class);
        });
    }

    @Test
    void enabledPublishesSanitizedHealthAndRefreshCounters() {
        contextRunner
                .withPropertyValues("platform.component.secret.enabled=true")
                .withUserConfiguration(DiagnosticsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasBean("secretRefreshHealthIndicator");
                    MeterBinder binder = context.getBean("secretRefreshMeterBinder", MeterBinder.class);
                    SecretRefreshDiagnostics diagnostics = context.getBean(SecretRefreshDiagnostics.class);
                    diagnostics.recordAttempt();
                    diagnostics.recordSuccess();
                    SimpleMeterRegistry registry = new SimpleMeterRegistry();
                    binder.bindTo(registry);
                    assertThat(registry.get("atlas.secret.refresh.attempts").gauge().value()).isEqualTo(1);
                    assertThat(registry.get("atlas.secret.refresh.successes").gauge().value()).isEqualTo(1);
                    assertThat(registry.get("atlas.secret.refresh.failures").gauge().value()).isZero();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DiagnosticsConfiguration {
        @Bean
        SecretRefreshDiagnostics secretRefreshDiagnostics() {
            return new SecretRefreshDiagnostics();
        }
    }
}
