/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.autoconfigure;

import cn.richie696.component.observability.core.ObservabilityState;
import cn.richie696.component.observability.core.ObservabilityPropagator;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Atlas Richie 可观测性统一 Spring Boot 自动配置。
 *
 * <p>OTel SDK、Exporter 和官方 Spring instrumentation 仍由官方
 * opentelemetry-spring-boot-starter 负责，本类只提供平台统一配置契约和有效开关状态。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    static BeanFactoryPostProcessor observabilityServiceNameNormalizer(
            ConfigurableEnvironment environment) {
        return beanFactory -> ObservabilityEnvironmentPostProcessor
                .normalizeServiceName(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservabilityState observabilityState(
            ObservabilityProperties properties,
            Environment environment) {
        boolean sdkDisabled = environment.getProperty("otel.sdk.disabled", Boolean.class, false);
        return new ObservabilityState(properties.isEnabled(), sdkDisabled);
    }

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean
    public ObservabilityPropagator observabilityPropagator(OpenTelemetry openTelemetry) {
        return new ObservabilityPropagator(
                openTelemetry.getPropagators().getTextMapPropagator());
    }
}
