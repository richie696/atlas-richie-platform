/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

/** Opaque provider connection resource with deterministic lifecycle. */
public interface VectorConnectionHandle extends AutoCloseable {

    VectorConnectionId id();

    VectorProvider provider();

    @Override
    void close();
}
