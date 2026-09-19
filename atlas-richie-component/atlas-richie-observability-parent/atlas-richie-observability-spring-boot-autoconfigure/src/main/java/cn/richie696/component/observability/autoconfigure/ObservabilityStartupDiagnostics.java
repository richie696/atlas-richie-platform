/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.autoconfigure;

import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 输出一次不包含敏感出口地址的启动诊断。
 *
 * <p>Exporter 只输出配置名称，不输出 endpoint，避免把 URL 中可能存在的用户名、密码或 Token
 * 写入日志。Resource 只输出标准的低敏感服务身份字段。</p>
 */
public final class ObservabilityStartupDiagnostics
        implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityStartupDiagnostics.class);
    private final AtomicBoolean logged = new AtomicBoolean();

    private final Environment environment;
    private final ObservabilityState state;
    private final OpenTelemetry openTelemetry;

    public ObservabilityStartupDiagnostics(
            Environment environment,
            ObservabilityState state,
            OpenTelemetry openTelemetry) {
        this.environment = environment;
        this.state = state;
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (logged.compareAndSet(false, true)) {
            LOGGER.info("Atlas Richie observability startup: {}", diagnosticLine());
        }
    }

    String diagnosticLine() {
        Map<String, String> resource = resourceAttributes();
        return "enabled=" + state.enabled()
                + ", configuredEnabled=" + state.configuredEnabled()
                + ", sdkDisabled=" + state.sdkDisabled()
                + ", openTelemetryBean=" + (openTelemetry != null)
                + ", resource=" + resource
                + ", exporters={traces=" + safeValue("otel.traces.exporter", "unset")
                + ", metrics=" + safeValue("otel.metrics.exporter", "unset")
                + ", logs=" + safeValue("otel.logs.exporter", "unset") + "}"
                + ", sampler=" + safeValue("otel.traces.sampler", "unset");
    }

    private Map<String, String> resourceAttributes() {
        Map<String, String> resource = new LinkedHashMap<>();
        putIfPresent(resource, "service.name", firstNonBlank(
                environment.getProperty("otel.service.name"),
                environment.getProperty("spring.application.name"),
                environment.getProperty("OTEL_SERVICE_NAME")));
        String configuredAttributes = environment.getProperty("otel.resource.attributes");
        if (configuredAttributes != null) {
            for (String item : configuredAttributes.split(",")) {
                String[] pair = item.split("=", 2);
                if (pair.length == 2 && isSafeResourceKey(pair[0].strip())) {
                    putIfPresent(resource, pair[0].strip(), pair[1].strip());
                }
            }
        }
        return resource;
    }

    private String safeValue(String key, String fallback) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replace('\n', '_').replace('\r', '_').replace(',', '_');
    }

    private static boolean isSafeResourceKey(String key) {
        return key.equals("service.name")
                || key.equals("service.namespace")
                || key.equals("service.version")
                || key.equals("service.instance.id")
                || key.equals("service.criticality")
                || key.equals("deployment.environment.name")
                || key.equals("k8s.namespace.name")
                || key.equals("k8s.pod.name");
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, truncate(value));
        }
    }

    private static String truncate(String value) {
        String safe = value.replace('\n', '_').replace('\r', '_');
        return safe.length() <= 128 ? safe : safe.substring(0, 128);
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
