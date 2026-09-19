/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * 把 Logback 事件接入官方 OTel Logback Appender。
 *
 * <p>应用仍可以通过现有 Logback 配置输出 JSON stdout；该配置只补充 OTLP Logs
 * 出口，是否真正发送由标准 OTEL_LOGS_EXPORTER 和统一总开关决定。</p>
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetryAppender.class)
@ConditionalOnBean({OpenTelemetry.class, ObservabilityState.class})
public class ObservabilityLogbackAutoConfiguration {

    private static final String MDC_ATTRIBUTES = String.join(",",
            "trace_id", "span_id", "request_id", "operation", "stage",
            "status", "duration_ms", "error.type", "error.stage");

    @Bean
    public SmartLifecycle observabilityLogbackAppenderLifecycle(
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ObservabilityState state) {
        return new SmartLifecycle() {
            private boolean running;

            @Override
            public void start() {
                if (!running && state.enabled()) {
                    configureMdcAttributes();
                    openTelemetryProvider.ifAvailable(OpenTelemetryAppender::install);
                    running = true;
                }
            }

            @Override
            public void stop() {
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 100;
            }
        };
    }

    private static void configureMdcAttributes() {
        Object loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof LoggerContext context)) {
            return;
        }
        configureAppenders(context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders());
        for (Logger logger : context.getLoggerList()) {
            configureAppenders(logger.iteratorForAppenders());
        }
    }

    private static void configureAppenders(Iterator<? extends Appender<?>> appenders) {
        while (appenders.hasNext()) {
            configureAppender(appenders.next());
        }
    }

    private static void configureAppender(Appender<?> appender) {
        if (appender instanceof OpenTelemetryAppender telemetryAppender) {
            boolean started = telemetryAppender.isStarted();
            if (started) {
                telemetryAppender.stop();
            }
            telemetryAppender.setMdcAttributesIncluded(MDC_ATTRIBUTES);
            if (started) {
                telemetryAppender.start();
            }
        }
        if (appender instanceof AppenderAttachable<?> attachable) {
            configureAttachedAppenders(attachable.iteratorForAppenders());
        }
    }

    private static void configureAttachedAppenders(Iterator<? extends Appender<?>> appenders) {
        while (appenders.hasNext()) {
            configureAppender(appenders.next());
        }
    }
}
