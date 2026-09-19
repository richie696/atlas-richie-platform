/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.actuator;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 Actuator/Micrometer 指标沿完整方案的 OTLP 主路径发送到 Alloy。
 *
 * <p>Spring Boot 的 Actuator 默认只创建本地 MeterRegistry；OTel Spring Boot
 * Starter 也不会把所有 Micrometer Binder 自动桥接成 OTLP 指标。因此完整入口
 * 必须显式创建 Micrometer OTLP Registry。Prometheus Registry 仍作为兼容端点保留，
 * 但部署主路径由 OTLP 负责，避免把应用端点和抓取链路误当成两套标准。</p>
 */
@AutoConfiguration(after = ObservabilityActuatorAutoConfiguration.class)
@ConditionalOnClass({MeterRegistry.class, OtlpMeterRegistry.class})
@ConditionalOnProperty(name = "otel.metrics.exporter", havingValue = "otlp")
@ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
public class ObservabilityOtlpMetricsAutoConfiguration {

    @Bean(destroyMethod = "close")
    @Primary
    public OtlpMeterRegistry atlasRichieOtlpMeterRegistry(Environment environment) {
        OtlpConfig config = new EnvironmentOtlpConfig(environment);
        return new OtlpMeterRegistry(config, Clock.SYSTEM);
    }

    private static final class EnvironmentOtlpConfig implements OtlpConfig {
        private final Environment environment;

        private EnvironmentOtlpConfig(Environment environment) {
            this.environment = environment;
        }

        @Override
        public String get(String key) {
            return safeProperty("management.otlp.metrics.export." + key);
        }

        @Override
        public String url() {
            String endpoint = firstNonBlank(
                    safeProperty("otel.exporter.otlp.metrics.endpoint"),
                    safeProperty("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT"),
                    safeProperty("otel.exporter.otlp.endpoint"),
                    safeProperty("OTEL_EXPORTER_OTLP_ENDPOINT"),
                    "http://localhost:4318/v1/metrics");
            return endpoint.endsWith("/v1/metrics") ? endpoint : endpoint + "/v1/metrics";
        }

        @Override
        public Duration step() {
            return Duration.parse(firstNonBlank(
                    safeProperty("management.otlp.metrics.export.step"),
                    safeProperty("OTEL_METRIC_EXPORT_INTERVAL"),
                    "PT15S"));
        }

        @Override
        public Map<String, String> resourceAttributes() {
            Map<String, String> attributes = parseAttributes(
                    firstNonBlank(safeProperty("otel.resource.attributes"),
                            safeProperty("OTEL_RESOURCE_ATTRIBUTES")));
            String serviceName = firstNonBlank(
                    safeProperty("otel.service.name"),
                    safeProperty("OTEL_SERVICE_NAME"),
                    safeProperty("spring.application.name"));
            if (serviceName != null) {
                attributes.putIfAbsent("service.name", serviceName);
            }
            return attributes;
        }

        private static Map<String, String> parseAttributes(String raw) {
            Map<String, String> attributes = new LinkedHashMap<>();
            if (raw == null || raw.isBlank()) {
                return attributes;
            }
            for (String item : raw.split(",")) {
                int separator = item.indexOf('=');
                if (separator > 0 && separator < item.length() - 1) {
                    attributes.put(item.substring(0, separator).trim(),
                            item.substring(separator + 1).trim());
                }
            }
            return attributes;
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        private String safeProperty(String key) {
            try {
                return environment.getProperty(key);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                // Nacos/test profiles may intentionally publish nested placeholders whose
                // fallback property is absent. Metrics must not make the application fail
                // during startup; the remaining defaults are still valid.
                return null;
            }
        }
    }
}
