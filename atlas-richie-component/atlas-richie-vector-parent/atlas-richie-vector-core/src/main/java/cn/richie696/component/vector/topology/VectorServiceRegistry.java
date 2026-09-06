/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.List;
import java.util.Optional;

/** Read-only registry for exact, application-composition-time store routing. */
public interface VectorServiceRegistry {

    Optional<VectorStoreHandle> find(VectorStoreId storeId);

    VectorStoreHandle require(VectorStoreId storeId);

    List<VectorStoreDescriptor> describeStores();
}
