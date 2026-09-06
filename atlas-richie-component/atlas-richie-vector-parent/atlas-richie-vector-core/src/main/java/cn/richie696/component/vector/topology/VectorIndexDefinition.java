/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable logical-to-physical index declaration. */
public final class VectorIndexDefinition {

    private final String id;
    private final String name;
    private final int dimension;
    private final String metric;
    private final String indexType;
    private final int replicas;
    private final int shards;
    private final Map<String, Object> additionalFields;
    private final Map<String, Object> indexParams;

    public VectorIndexDefinition(
            String id,
            String name,
            int dimension,
            String metric,
            String indexType,
            int replicas,
            int shards,
            Map<String, Object> additionalFields,
            Map<String, Object> indexParams) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be greater than zero: " + id);
        }
        if (replicas <= 0) {
            throw new IllegalArgumentException("replicas must be greater than zero: " + id);
        }
        if (shards <= 0) {
            throw new IllegalArgumentException("shards must be greater than zero: " + id);
        }
        this.dimension = dimension;
        this.metric = requireText(metric, "metric");
        this.indexType = requireText(indexType, "indexType");
        this.replicas = replicas;
        this.shards = shards;
        this.additionalFields = immutableMap(additionalFields);
        this.indexParams = immutableMap(indexParams);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int dimension() {
        return dimension;
    }

    public String metric() {
        return metric;
    }

    public String indexType() {
        return indexType;
    }

    public int replicas() {
        return replicas;
    }

    public int shards() {
        return shards;
    }

    public Map<String, Object> additionalFields() {
        return additionalFields;
    }

    public Map<String, Object> indexParams() {
        return indexParams;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source == null ? Map.of() : source));
    }
}
