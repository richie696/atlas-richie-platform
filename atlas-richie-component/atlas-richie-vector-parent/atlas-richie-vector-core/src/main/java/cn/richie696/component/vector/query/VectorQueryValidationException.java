/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.diagnostics.VectorCategorizedError;
import cn.richie696.component.vector.diagnostics.VectorErrorCategory;

import java.util.Objects;

/** Validation failure with a stable code and payload-free message. */
public final class VectorQueryValidationException extends IllegalArgumentException implements VectorCategorizedError {

    private final VectorQueryErrorCode code;

    public VectorQueryValidationException(VectorQueryErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public VectorQueryErrorCode code() {
        return code;
    }

    @Override
    public VectorErrorCategory errorCategory() {
        return VectorErrorCategory.QUERY_REJECTED;
    }
}
