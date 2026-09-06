/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

/** Stable validation categories for advanced query contracts. */
public enum VectorQueryErrorCode {
    INVALID_VALUE,
    LIMIT_EXCEEDED,
    TIMEOUT,
    CONCURRENCY_LIMIT,
    UNSUPPORTED_OPTION,
    CONFLICTING_OPTIONS
}
