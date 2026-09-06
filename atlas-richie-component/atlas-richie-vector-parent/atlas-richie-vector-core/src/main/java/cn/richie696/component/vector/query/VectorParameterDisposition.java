/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

/** How an effective query parameter was handled by the provider adapter. */
public enum VectorParameterDisposition {
    APPLIED,
    DEFAULTED,
    REJECTED,
    IGNORED
}
