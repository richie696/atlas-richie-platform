/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.exceptions;

/** Raised when a caller is not allowed to resolve a registered vector Store. */
public final class VectorStoreAccessDeniedException extends SecurityException {

    public VectorStoreAccessDeniedException() {
        super("vector store access denied");
    }
}
