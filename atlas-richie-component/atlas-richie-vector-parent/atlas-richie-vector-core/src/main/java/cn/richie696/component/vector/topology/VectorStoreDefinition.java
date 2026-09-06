/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.model.Modality;
import cn.richie696.component.vector.query.VectorQueryDefaults;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable logical store definition, independent of any consumer project's domain model. */
public final class VectorStoreDefinition {

    private final VectorStoreId id;
    private final VectorConnectionId connectionId;
    private final String embeddingModelRef;
    private final EmbeddingNormalization embeddingNormalization;
    private final Set<Modality> embeddingModalities;
    private final String defaultIndex;
    private final boolean required;
    private final Set<String> requiredCapabilities;
    private final VectorQueryDefaults queryDefaults;
    private final Map<String, VectorIndexDefinition> indexes;

    public VectorStoreDefinition(
            VectorStoreId id,
            VectorConnectionId connectionId,
            String embeddingModelRef,
            String defaultIndex,
            boolean required,
            Set<String> requiredCapabilities) {
        this(id, connectionId, embeddingModelRef, EmbeddingNormalization.UNSPECIFIED, Set.of(Modality.TEXT),
                defaultIndex, required, requiredCapabilities, null, Map.of());
    }

    public VectorStoreDefinition(
            VectorStoreId id,
            VectorConnectionId connectionId,
            String embeddingModelRef,
            String defaultIndex,
            boolean required,
            Set<String> requiredCapabilities,
            Map<String, VectorIndexDefinition> indexes) {
        this(id, connectionId, embeddingModelRef, EmbeddingNormalization.UNSPECIFIED, Set.of(Modality.TEXT),
                defaultIndex, required, requiredCapabilities, null, indexes);
    }

    public VectorStoreDefinition(
            VectorStoreId id,
            VectorConnectionId connectionId,
            String embeddingModelRef,
            EmbeddingNormalization embeddingNormalization,
            Set<Modality> embeddingModalities,
            String defaultIndex,
            boolean required,
            Set<String> requiredCapabilities,
            Map<String, VectorIndexDefinition> indexes) {
        this(id, connectionId, embeddingModelRef, embeddingNormalization, embeddingModalities,
                defaultIndex, required, requiredCapabilities, null, indexes);
    }

    public VectorStoreDefinition(
            VectorStoreId id,
            VectorConnectionId connectionId,
            String embeddingModelRef,
            EmbeddingNormalization embeddingNormalization,
            Set<Modality> embeddingModalities,
            String defaultIndex,
            boolean required,
            Set<String> requiredCapabilities,
            VectorQueryDefaults queryDefaults,
            Map<String, VectorIndexDefinition> indexes) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId must not be null");
        this.embeddingModelRef = requireText(embeddingModelRef, "embeddingModelRef");
        this.embeddingNormalization = Objects.requireNonNull(
                embeddingNormalization, "embeddingNormalization must not be null");
        if (embeddingModalities == null || embeddingModalities.isEmpty()
                || embeddingModalities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("embeddingModalities must not be empty");
        }
        this.embeddingModalities = Collections.unmodifiableSet(new LinkedHashSet<>(
                embeddingModalities));
        this.defaultIndex = requireText(defaultIndex, "defaultIndex");
        this.required = required;
        this.requiredCapabilities = Collections.unmodifiableSet(new LinkedHashSet<>(
                requiredCapabilities == null ? Set.of() : requiredCapabilities));
        if (this.requiredCapabilities.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("requiredCapabilities must not contain blank values");
        }
        this.queryDefaults = queryDefaults;
        this.indexes = Collections.unmodifiableMap(new LinkedHashMap<>(indexes == null ? Map.of() : indexes));
        if (this.indexes.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || !entry.getKey().equals(entry.getValue().id()))) {
            throw new IllegalArgumentException("index map keys must match non-null index definition ids");
        }
    }

    public VectorStoreId id() {
        return id;
    }

    public VectorConnectionId connectionId() {
        return connectionId;
    }

    public String embeddingModelRef() {
        return embeddingModelRef;
    }

    public EmbeddingNormalization embeddingNormalization() {
        return embeddingNormalization;
    }

    public Set<Modality> embeddingModalities() {
        return embeddingModalities;
    }

    public String defaultIndex() {
        return defaultIndex;
    }

    public boolean required() {
        return required;
    }

    public Set<String> requiredCapabilities() {
        return requiredCapabilities;
    }

    public VectorQueryDefaults queryDefaults() {
        return queryDefaults;
    }

    public Map<String, VectorIndexDefinition> indexes() {
        return indexes;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
