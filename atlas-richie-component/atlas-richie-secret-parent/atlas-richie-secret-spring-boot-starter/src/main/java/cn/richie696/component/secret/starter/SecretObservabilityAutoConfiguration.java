/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Optional Actuator/Micrometer bridge; absent dependencies keep Secret transparent. */
@AutoConfiguration(after = SecretRuntimeAutoConfiguration.class)
@ConditionalOnProperty(prefix = BootstrapSecretProperties.PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnBean(SecretRefreshDiagnostics.class)
@ConditionalOnClass({HealthIndicator.class, MeterBinder.class})
public class SecretObservabilityAutoConfiguration {

    @Bean("secretRefreshHealthIndicator")
    @ConditionalOnMissingBean(name = "secretRefreshHealthIndicator")
    public HealthIndicator secretRefreshHealthIndicator(
            SecretRefreshDiagnostics diagnostics,
            Environment environment) {
        BootstrapSecretProperties properties = Binder.get(environment)
                .bind(BootstrapSecretProperties.PREFIX, BootstrapSecretProperties.class)
                .orElseGet(BootstrapSecretProperties::new);
        return new SecretRefreshHealthIndicator(diagnostics, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "secretRefreshMeterBinder")
    public MeterBinder secretRefreshMeterBinder(SecretRefreshDiagnostics diagnostics) {
        return registry -> {
            Gauge.builder("atlas.secret.refresh.attempts", diagnostics,
                            value -> value.snapshot().attempts())
                    .description("Secret refresh attempts")
                    .register(registry);
            Gauge.builder("atlas.secret.refresh.successes", diagnostics,
                            value -> value.snapshot().successes())
                    .description("Successful Secret refreshes")
                    .register(registry);
            Gauge.builder("atlas.secret.refresh.failures", diagnostics,
                            value -> value.snapshot().failures())
                    .description("Failed Secret refreshes")
                    .register(registry);
            Gauge.builder("atlas.secret.refresh.listener.failures", diagnostics,
                            value -> value.snapshot().listenerFailures())
                    .description("Secret snapshot listener failures")
                    .register(registry);
        };
    }
}
