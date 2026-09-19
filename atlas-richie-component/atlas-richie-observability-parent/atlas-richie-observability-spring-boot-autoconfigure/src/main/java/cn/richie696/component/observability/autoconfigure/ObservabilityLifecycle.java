/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.autoconfigure;

import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.util.concurrent.TimeUnit;

/**
 * 在 Spring Context 关闭前请求官方 SDK flush。
 *
 * <p>SDK/provider/exporter 的创建和最终 shutdown 仍由官方 OTel Spring Boot Starter 管理；本类
 * 只负责在 ContextClosedEvent 阶段提前 flush，避免业务线程停止后仍有在途观测数据。</p>
 */
public final class ObservabilityLifecycle
        implements ApplicationListener<ContextClosedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityLifecycle.class);
    private static final long FLUSH_TIMEOUT_SECONDS = 15;

    private final ObservabilityState state;
    private final OpenTelemetry openTelemetry;

    public ObservabilityLifecycle(ObservabilityState state, OpenTelemetry openTelemetry) {
        this.state = state;
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (!state.enabled() || !(openTelemetry instanceof OpenTelemetrySdk sdk)) {
            return;
        }
        boolean traceFlushed = sdk.getSdkTracerProvider()
                .forceFlush().join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS).isSuccess();
        boolean metricFlushed = sdk.getSdkMeterProvider()
                .forceFlush().join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS).isSuccess();
        boolean logFlushed = sdk.getSdkLoggerProvider()
                .forceFlush().join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS).isSuccess();
        if (!traceFlushed || !metricFlushed || !logFlushed) {
            LOGGER.warn("Atlas Richie observability force flush was incomplete: traces={}, metrics={}, logs={}",
                    traceFlushed, metricFlushed, logFlushed);
        } else {
            LOGGER.debug("Atlas Richie observability exporters force flushed before shutdown");
        }
    }
}
