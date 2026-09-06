/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

/** Provider-neutral health state; UNKNOWN means that no health capability is exposed. */
public enum VectorStoreHealthStatus {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}
