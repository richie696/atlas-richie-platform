/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.core;

/**
 * Protocol-independent dependency metrics contract.
 *
 * <p>Protocol components depend on this small contract instead of depending on
 * Actuator, Micrometer, or a concrete exporter. The Actuator module provides the
 * default implementation when metrics are enabled.</p>
 */
public interface DependencyMetricsRecorder {

    /**
     * Records one completed dependency request.
     */
    void recordRequest(
            String dependencyType,
            String targetService,
            String operation,
            String status,
            long durationNanos);

    /**
     * Records the current number of active connections for a dependency pool.
     */
    void recordConnection(String dependencyType, String targetService, long activeConnections);
}
