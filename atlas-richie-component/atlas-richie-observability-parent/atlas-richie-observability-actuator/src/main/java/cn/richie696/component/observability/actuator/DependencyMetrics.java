/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.actuator;

import cn.richie696.component.observability.core.ObservabilityState;
import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DB、Redis、HTTP、gRPC、NATS 和 MCP 适配器共享的低基数依赖指标门面。
 *
 * <p>该类不依赖任何具体客户端。协议或连接池组件只传入稳定的 dependency_type、target_service、
 * operation 和 status；具体 SDK 适配留在对应组件中。</p>
 */
public final class DependencyMetrics implements DependencyMetricsRecorder {

    public static final String REQUESTS = "dependency.request";
    public static final String CONNECTIONS = "dependency.connection";

    private final MeterRegistry registry;
    private final ObjectProvider<MeterRegistry> registryProvider;
    private final ObservabilityState state;
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> connectionGauges = new ConcurrentHashMap<>();

    public DependencyMetrics(MeterRegistry registry, ObservabilityState state) {
        this.registry = registry;
        this.registryProvider = null;
        this.state = Objects.requireNonNull(state, "state");
    }

    /**
     * Creates a recorder that resolves the registry lazily. This is important for
     * applications where the OTLP registry is created by another auto-configuration
     * after this component has been instantiated.
     */
    public DependencyMetrics(ObjectProvider<MeterRegistry> registryProvider, ObservabilityState state) {
        this.registry = null;
        this.registryProvider = Objects.requireNonNull(registryProvider, "registryProvider");
        this.state = Objects.requireNonNull(state, "state");
    }

    public void recordRequest(
            String dependencyType,
            String targetService,
            String operation,
            String status,
            long durationNanos) {
        MeterRegistry activeRegistry = registry();
        if (activeRegistry == null || state.disabled()) {
            return;
        }
        Tags tags = tags(dependencyType, targetService, operation, status);
        String key = tags.toString();
        timers.computeIfAbsent(key, ignored -> Timer.builder(REQUESTS)
                        .tags(tags)
                        .description("Dependency request duration")
                        .publishPercentileHistogram()
                        .register(activeRegistry))
                .record(Math.max(0L, durationNanos), java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public void recordConnection(
            String dependencyType,
            String targetService,
            long activeConnections) {
        MeterRegistry activeRegistry = registry();
        if (activeRegistry == null || state.disabled()) {
            return;
        }
        Tags tags = Tags.of(
                "dependency_type", safeTag(dependencyType, "unknown"),
                "target_service", safeTag(targetService, "unknown"));
        String key = tags.toString();
        AtomicLong value = connectionGauges.computeIfAbsent(key, ignored -> new AtomicLong());
        value.set(Math.max(0L, activeConnections));
        activeRegistry.gauge(CONNECTIONS, tags, value, AtomicLong::doubleValue);
    }

    private MeterRegistry registry() {
        return registry != null ? registry : registryProvider.getIfAvailable();
    }

    private static Tags tags(
            String dependencyType,
            String targetService,
            String operation,
            String status) {
        return Tags.of(
                "dependency_type", safeTag(dependencyType, "unknown"),
                "target_service", safeTag(targetService, "unknown"),
                "operation", safeTag(operation, "unknown"),
                "status", safeTag(status, "unknown"));
    }

    private static String safeTag(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String safe = value.replace('\n', '_').replace('\r', '_').replace(',', '_');
        return safe.length() <= 128 ? safe : safe.substring(0, 128);
    }
}
