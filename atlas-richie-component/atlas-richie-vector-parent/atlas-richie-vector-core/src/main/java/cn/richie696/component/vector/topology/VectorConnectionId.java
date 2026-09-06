/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.regex.Pattern;

/** Stable application-local identifier for one physical provider connection. */
public record VectorConnectionId(String value) {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*");

    public VectorConnectionId {
        if (value == null || !VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("vector connection id must match " + VALID_ID.pattern() + ": " + value);
        }
    }

    public static VectorConnectionId of(String value) {
        return new VectorConnectionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
