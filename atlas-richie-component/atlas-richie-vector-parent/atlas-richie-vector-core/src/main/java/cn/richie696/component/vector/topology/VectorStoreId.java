/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.regex.Pattern;

/** Stable logical vector store identifier used by application composition code. */
public record VectorStoreId(String value) {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*");

    public VectorStoreId {
        if (value == null || !VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("vector store id must match " + VALID_ID.pattern() + ": " + value);
        }
    }

    public static VectorStoreId of(String value) {
        return new VectorStoreId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
