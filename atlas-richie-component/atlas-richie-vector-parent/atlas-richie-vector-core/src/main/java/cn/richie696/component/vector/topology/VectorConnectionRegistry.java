/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

import java.util.List;
import java.util.Optional;

/** Application-local registry and lifecycle owner for physical vector connections. */
public interface VectorConnectionRegistry extends AutoCloseable {

    VectorConnectionHandle open(VectorConnectionDefinition definition);

    Optional<VectorConnectionHandle> find(VectorConnectionId connectionId);

    VectorConnectionHandle require(VectorConnectionId connectionId);

    VectorProviderFactory requireFactory(VectorProvider provider);

    List<VectorConnectionId> openedConnectionIds();

    void closeConnection(VectorConnectionId connectionId);

    @Override
    void close();
}
