/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.autoconfigure;

import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 统一启动诊断和关闭前 flush 配置。
 */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnBean(ObservabilityState.class)
public class ObservabilityDiagnosticsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObservabilityStartupDiagnostics observabilityStartupDiagnostics(
            Environment environment,
            ObservabilityState state,
            ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        return new ObservabilityStartupDiagnostics(
                environment, state, openTelemetryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservabilityLifecycle observabilityLifecycle(
            ObservabilityState state,
            ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        return new ObservabilityLifecycle(state, openTelemetryProvider.getIfAvailable());
    }
}
