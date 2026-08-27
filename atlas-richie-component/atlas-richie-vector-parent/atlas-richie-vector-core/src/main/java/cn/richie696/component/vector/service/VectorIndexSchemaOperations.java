/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service;

import java.util.Set;

/**
 * Optional provider capability for reading the physical scalar schema of an index.
 *
 * <p>Business code must use this capability before switching a rebuilt index into
 * production.  A collection can exist and contain vectors while still missing
 * mandatory ACL fields, which would make every filtered retrieval return empty.</p>
 */
public interface VectorIndexSchemaOperations {

    /**
     * Return the explicitly declared field names of a physical index.
     */
    Set<String> getFieldNames(String indexName);
}
