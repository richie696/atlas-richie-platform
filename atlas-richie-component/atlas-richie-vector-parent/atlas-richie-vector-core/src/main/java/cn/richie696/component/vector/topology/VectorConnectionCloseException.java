/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.List;

/** Sanitized aggregate close failure that never includes provider settings or exception messages. */
public final class VectorConnectionCloseException extends RuntimeException {

    private final List<VectorConnectionId> failedConnectionIds;

    public VectorConnectionCloseException(List<VectorConnectionId> failedConnectionIds) {
        super("failed to close vector connections: " + failedConnectionIds);
        this.failedConnectionIds = List.copyOf(failedConnectionIds);
    }

    public List<VectorConnectionId> failedConnectionIds() {
        return failedConnectionIds;
    }
}
