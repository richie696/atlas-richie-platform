/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.exceptions;

import cn.richie696.component.vector.diagnostics.VectorCategorizedError;
import cn.richie696.component.vector.diagnostics.VectorErrorCategory;

/** A requested typed capability is not exposed by the selected Store. */
public final class VectorCapabilityMismatchException extends UnsupportedOperationException
        implements VectorCategorizedError {

    public VectorCapabilityMismatchException(String message) {
        super(message);
    }

    @Override
    public VectorErrorCategory errorCategory() {
        return VectorErrorCategory.CAPABILITY_MISMATCH;
    }
}
