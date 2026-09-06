/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

import cn.richie696.component.vector.topology.VectorStoreId;

/** Optional pull-based diagnostics entry point; it performs no work until invoked. */
public interface VectorStoreDiagnostics {

    VectorStoreHealthSnapshot check(VectorStoreId storeId);

    VectorHealthReport checkAll();
}
