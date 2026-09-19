/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.actuator;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Atlas Richie Actuator/Micrometer 统一配置。
 *
 * <p>JVM、GC、线程、类加载、文件描述符、HTTP 和常见连接池优先复用 Spring Boot
 * Actuator/Micrometer 官方 Binder。本类只补总开关、Resource 级公共标签和中台线程池 Binder。</p>
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityActuatorAutoConfiguration {

    @Bean(name = "atlasRichieObservabilityMeterFilter")
    @ConditionalOnMissingBean(name = "atlasRichieObservabilityMeterFilter")
    public MeterFilter observabilityMeterFilter(ObservabilityState state) {
        return state.enabled() ? MeterFilter.accept() : MeterFilter.deny();
    }

    @Bean
    @ConditionalOnMissingBean(name = "atlasRichieObservabilityCommonTags")
    public MeterFilter observabilityCommonTags(
            Environment environment) {
        Map<String, String> commonTags = commonTags(environment);
        if (commonTags.isEmpty()) {
            return MeterFilter.accept();
        }
        Tags tags = Tags.of(commonTags.entrySet().stream()
                .map(entry -> io.micrometer.core.instrument.Tag.of(entry.getKey(), entry.getValue()))
                .toList());
        return MeterFilter.commonTags(tags);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorMetricsBinder observabilityExecutorMetricsBinder(
            ApplicationContext applicationContext,
            ObjectProvider<ObservabilityState> stateProvider) {
        ObservabilityState state = stateProvider.getIfAvailable(ObservabilityState::enabledState);
        return new ExecutorMetricsBinder(applicationContext.getBeansOfType(Executor.class), state);
    }

    @Bean
    @ConditionalOnMissingBean
    public DependencyMetrics observabilityDependencyMetrics(
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObservabilityState state) {
        return new DependencyMetrics(meterRegistryProvider, state);
    }

    private static Map<String, String> commonTags(Environment environment) {
        Map<String, String> tags = new LinkedHashMap<>();
        String serviceName = firstNonBlank(
                safeProperty(environment, "otel.service.name"),
                safeProperty(environment, "spring.application.name"),
                safeProperty(environment, "OTEL_SERVICE_NAME"));
        String environmentName = firstNonBlank(
                safeProperty(environment, "otel.resource.attributes.deployment.environment.name"),
                safeProperty(environment, "deployment.environment.name"));
        if (serviceName != null) {
            tags.put("service.name", serviceName);
        }
        if (environmentName != null) {
            tags.put("deployment.environment.name", environmentName);
        }
        return tags;
    }

    private static String safeProperty(Environment environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
