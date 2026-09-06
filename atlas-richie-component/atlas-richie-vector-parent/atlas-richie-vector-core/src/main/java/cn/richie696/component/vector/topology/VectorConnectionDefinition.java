/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable provider connection definition. Its string form never exposes settings. */
public final class VectorConnectionDefinition {

    private final VectorConnectionId id;
    private final VectorProvider provider;
    private final Map<String, Object> settings;

    public VectorConnectionDefinition(VectorConnectionId id, VectorProvider provider, Map<String, Object> settings) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(
                settings == null ? Map.of() : settings));
    }

    public VectorConnectionId id() {
        return id;
    }

    public VectorProvider provider() {
        return provider;
    }

    public Map<String, Object> settings() {
        return settings;
    }

    @Override
    public String toString() {
        return "VectorConnectionDefinition[id=" + id + ", provider=" + provider + ", settings=<redacted>]";
    }
}
