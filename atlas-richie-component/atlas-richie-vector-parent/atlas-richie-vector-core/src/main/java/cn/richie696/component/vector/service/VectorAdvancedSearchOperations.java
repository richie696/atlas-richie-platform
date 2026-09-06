/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service;

import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorSearchExecution;

/** Optional Store-bound, typed and versioned advanced query capability. */
@FunctionalInterface
public interface VectorAdvancedSearchOperations {

    VectorSearchExecution search(VectorQueryRequest request);
}
