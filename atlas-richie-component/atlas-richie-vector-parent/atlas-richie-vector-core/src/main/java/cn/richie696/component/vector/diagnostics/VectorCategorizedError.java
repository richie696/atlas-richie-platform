/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

/** Implemented by exceptions that expose a stable non-sensitive error category. */
public interface VectorCategorizedError {

    VectorErrorCategory errorCategory();
}
