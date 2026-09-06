/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.query.VectorQueryDefaults;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable provider-neutral topology definitions converted from configuration properties. */
public record VectorTopologyDefinitions(
        List<VectorConnectionDefinition> connections,
        List<VectorStoreDefinition> stores) {

    public VectorTopologyDefinitions {
        connections = List.copyOf(connections);
        stores = List.copyOf(stores);
    }

    public static VectorTopologyDefinitions from(VectorProperties properties) {
        properties.validateNamedTopology();
        List<VectorConnectionDefinition> connections = properties.getConnections().entrySet().stream()
                .map(entry -> new VectorConnectionDefinition(
                        VectorConnectionId.of(entry.getKey()),
                        entry.getValue().getProvider(),
                        entry.getValue().getSettings()))
                .toList();
        List<VectorStoreDefinition> stores = properties.getStores().entrySet().stream()
                .map(entry -> toStore(entry.getKey(), entry.getValue()))
                .toList();
        return new VectorTopologyDefinitions(connections, stores);
    }

    private static VectorStoreDefinition toStore(String storeId, VectorProperties.StoreConfig config) {
        Map<String, VectorIndexDefinition> indexes = new LinkedHashMap<>();
        config.getIndexes().forEach((indexId, index) -> indexes.put(indexId, new VectorIndexDefinition(
                indexId,
                index.getName() == null || index.getName().isBlank() ? indexId : index.getName(),
                index.getDimension(),
                index.getMetric(),
                index.getIndexType(),
                index.getReplicas(),
                index.getShards(),
                index.getAdditionalFields(),
                index.getIndexParams())));
        return new VectorStoreDefinition(
                VectorStoreId.of(storeId),
                VectorConnectionId.of(config.getConnectionRef()),
                config.getEmbeddingModelRef(),
                config.getEmbeddingNormalization(),
                config.getEmbeddingModalities(),
                config.getDefaultIndex(),
                config.isRequired(),
                config.getRequiredCapabilities(),
                queryDefaults(config.getQueryDefaults()),
                indexes);
    }

    private static VectorQueryDefaults queryDefaults(VectorProperties.QueryDefaultsConfig config) {
        if (config == null) return null;
        return new VectorQueryDefaults(
                config.getTopK(),
                config.getMinScore(),
                config.getCandidateLimit(),
                config.getTimeout(),
                config.getConsistency(),
                config.getReturnFields());
    }
}
