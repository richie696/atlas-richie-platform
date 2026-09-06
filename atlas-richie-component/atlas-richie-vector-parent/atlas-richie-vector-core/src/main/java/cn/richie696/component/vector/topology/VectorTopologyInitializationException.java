/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Sanitized required-store startup failure. */
public final class VectorTopologyInitializationException extends RuntimeException {

    private final VectorStoreStartupFailure failure;

    public VectorTopologyInitializationException(VectorStoreStartupFailure failure) {
        super("required vector store failed to initialize: " + failure.storeId()
                + " (phase=" + failure.phase() + ", type=" + failure.failureType() + ")");
        this.failure = failure;
    }

    public VectorStoreStartupFailure failure() {
        return failure;
    }
}
