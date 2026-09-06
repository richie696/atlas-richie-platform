/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.exceptions;

import cn.richie696.component.vector.diagnostics.VectorCategorizedError;
import cn.richie696.component.vector.diagnostics.VectorErrorCategory;

/** Optional normalized wrapper for a Provider transport or availability failure. */
public final class VectorProviderUnavailableException extends RuntimeException
        implements VectorCategorizedError {

    public VectorProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public VectorErrorCategory errorCategory() {
        return VectorErrorCategory.PROVIDER_UNAVAILABLE;
    }
}
