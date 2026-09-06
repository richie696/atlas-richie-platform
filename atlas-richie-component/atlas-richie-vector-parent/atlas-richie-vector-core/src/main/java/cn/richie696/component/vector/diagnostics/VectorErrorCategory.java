/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

/** Stable, payload-free error categories for routing and operational diagnostics. */
public enum VectorErrorCategory {
    NONE,
    ROUTE_NOT_FOUND,
    CAPABILITY_MISMATCH,
    PROVIDER_UNAVAILABLE,
    HEALTH_CHECK_FAILED,
    STARTUP_FAILURE,
    QUERY_REJECTED,
    OPERATION_FAILED
}
