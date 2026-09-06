/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

/** Bounded operation names suitable for metric labels. */
public enum VectorStoreOperation {
    SEARCH_TEXT,
    SEARCH_IMAGE,
    SEARCH_TUNED,
    SEARCH_HYBRID,
    SEARCH_RERANK,
    SEARCH_MMR,
    UPSERT,
    DELETE_ONE,
    DELETE_BATCH,
    BULK_UPSERT,
    BULK_DELETE
}
