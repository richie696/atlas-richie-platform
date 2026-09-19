/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把平台总开关映射到官方 OTel 和 Actuator 配置。
 *
 * <p>必须在自动配置绑定前执行，确保只配置 atlas.observability.enabled=false
 * 就能阻止 OTel SDK/exporter 初始化，并阻止 Prometheus 指标出口。</p>
 */
public class ObservabilityEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final String APPLICATION_SWITCH = "atlas.observability.enabled";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        Boolean enabled = environment.getProperty(APPLICATION_SWITCH, Boolean.class);
        boolean sdkDisabled = environment.getProperty("otel.sdk.disabled", Boolean.class, false)
                || environment.getProperty("OTEL_SDK_DISABLED", Boolean.class, false);
        Map<String, Object> properties = new LinkedHashMap<>();
        if (Boolean.FALSE.equals(enabled) || sdkDisabled) {
            properties.put("otel.sdk.disabled", "true");
            properties.put("management.metrics.enable.all", "false");
            // Spring Boot 4 rejects the legacy `enabled` property when an
            // application already uses the access-level endpoint contract.
            // Close the endpoint using the matching contract instead of
            // creating a mutually-exclusive property pair.
            if (environment.containsProperty("management.endpoint.prometheus.access")) {
                properties.put("management.endpoint.prometheus.access", "none");
            } else {
                properties.put("management.endpoint.prometheus.enabled", "false");
            }
            properties.put("management.metrics.export.prometheus.enabled", "false");
            putIfAbsent(environment, properties,
                    "management.endpoint.health.probes.enabled", "true");
        } else {
            putIfAbsent(environment, properties,
                    "management.endpoints.web.exposure.include", "health,info,prometheus");
            putIfAbsent(environment, properties,
                    "management.endpoint.health.probes.enabled", "true");
        }

        // Nacos and other remote config stores may return the raw nested Spring
        // placeholder to the official OTel autoconfigurator. OTel resolves its
        // own property set and cannot resolve `${spring.application.name}` in
        // that value, so normalize it at the platform boundary first.
        String serviceName = safeProperty(environment, "otel.service.name");
        if (isUnresolved(serviceName)) {
            String fallback = safeProperty(environment, "OTEL_SERVICE_NAME");
            if (isUnresolved(fallback) || fallback == null || fallback.isBlank()) {
                fallback = safeProperty(environment, "spring.application.name");
            }
            if (isUnresolved(fallback) || fallback == null || fallback.isBlank()) {
                fallback = "atlas-application";
            }
            properties.put("otel.service.name", fallback);
        }

        String resourceAttributes = environment.getProperty("otel.resource.attributes");
        resourceAttributes = appendIfMissing(
                resourceAttributes,
                "service.namespace",
                environment.getProperty("atlas.observability.service-namespace"));
        resourceAttributes = appendIfMissing(
                resourceAttributes,
                "service.criticality",
                environment.getProperty("atlas.observability.criticality"));
        if (resourceAttributes != null && !resourceAttributes.isBlank()) {
            properties.put("otel.resource.attributes", resourceAttributes);
        }
        if (properties.isEmpty()) {
            return;
        }

        environment.getPropertySources().addFirst(
                new MapPropertySource("atlasRichieObservability", properties));
        // OTel's SpringConfigProperties uses Boot's configuration-property
        // resolver. Re-attach it so the late high-precedence source is visible
        // even when ConfigData (for example Nacos) initialized it earlier.
        ConfigurationPropertySources.attach(environment);
    }

    private static void putIfAbsent(
            ConfigurableEnvironment environment,
            Map<String, Object> properties,
            String key,
            String value) {
        if (!environment.containsProperty(key)) {
            properties.put(key, value);
        }
    }

    private static String appendIfMissing(String existing, String key, String value) {
        if (value == null || value.isBlank()) {
            return existing;
        }
        String attributes = existing == null ? "" : existing;
        for (String item : attributes.split(",")) {
            if (item.stripLeading().startsWith(key + "=")) {
                return attributes;
            }
        }
        return attributes.isBlank() ? key + "=" + value : attributes + "," + key + "=" + value;
    }

    private static String safeProperty(ConfigurableEnvironment environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isUnresolved(String value) {
        return value != null && value.contains("${");
    }

    static void normalizeServiceName(ConfigurableEnvironment environment) {
        String serviceName = safeProperty(environment, "otel.service.name");
        if (!isUnresolved(serviceName)) {
            return;
        }
        String fallback = safeProperty(environment, "OTEL_SERVICE_NAME");
        if (isUnresolved(fallback) || fallback == null || fallback.isBlank()) {
            fallback = safeProperty(environment, "spring.application.name");
        }
        if (isUnresolved(fallback) || fallback == null || fallback.isBlank()) {
            fallback = "atlas-application";
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "atlasRichieObservabilityServiceName",
                Map.of("otel.service.name", fallback)));
        ConfigurationPropertySources.attach(environment);
    }

    @Override
    public int getOrder() {
        // Run after ConfigData imports (including Nacos) have populated the
        // environment, but before the application context creates OTel beans.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
