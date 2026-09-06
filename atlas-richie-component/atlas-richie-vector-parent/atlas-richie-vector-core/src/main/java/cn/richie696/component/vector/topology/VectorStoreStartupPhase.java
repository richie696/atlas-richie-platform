/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Sanitized phase classification for named store startup failures. */
public enum VectorStoreStartupPhase {
    CONNECTION,
    EMBEDDING_MODEL,
    STORE_CREATION,
    STORE_VALIDATION
}
