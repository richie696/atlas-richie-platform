/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.filter.WeaviateVectorFilterCompiler;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.VectorHybridSearchOperations;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.VectorRecordReadOperations;
import cn.richie696.component.vector.service.impl.WeaviateVectorServiceImpl;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorCapabilityDescriptor;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorScoreThresholdExecution;
import cn.richie696.component.vector.topology.VectorScoreThresholdKind;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStoreOptions;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named Multi-store factory for Weaviate. */
public final class WeaviateVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "scheme", "host", "api-key", "connection-timeout-ms",
            "connection-request-timeout-ms", "socket-timeout-ms");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of(
            "consistency-level", "filter-metadata-fields");
    private static final Set<String> METRICS = Set.of(
            "cosine", "dot", "l2-squared", "hamming", "manhattan");
    private static final VectorStoreCapabilities CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0",
                    Map.of("filter-stage", "provider-recall", "transport", "graphql-where")),
            new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0",
                    Map.of("filter-stage", "provider-recall")),
            new VectorCapabilityDescriptor(VectorCapability.ACL_SAFE_HYBRID, "1.0",
                    Map.of("single-provider-query", "true", "branches", "bm25,vector")),
            new VectorCapabilityDescriptor(VectorCapability.QUERY_TUNING, "1.0",
                    Map.of("supported", "hybrid.alpha")),
            VectorCapabilityDescriptor.supported(VectorCapability.SCORE_STAGES),
            VectorCapabilityDescriptor.supported(VectorCapability.INDEX_LIFECYCLE)));

    private final RerankService rerankService;

    public WeaviateVectorProviderFactory(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.WEAVIATE;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "Weaviate connection settings");
        String scheme = text(definition.settings(), "scheme", "http").toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Weaviate scheme must be http or https");
        }
        text(definition.settings(), "host", "localhost:8080");
        nullableText(definition.settings(), "api-key");
        positiveInteger(definition.settings(), "connection-timeout-ms", 10_000);
        positiveInteger(definition.settings(), "connection-request-timeout-ms", 10_000);
        positiveInteger(definition.settings(), "socket-timeout-ms", 60_000);
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() != 1) {
            throw new IllegalArgumentException("Weaviate named Store requires exactly one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        if (!index.name().matches("[A-Z][A-Za-z0-9_]{0,254}")) {
            throw new IllegalArgumentException("invalid Weaviate class name");
        }
        if (!"hnsw".equalsIgnoreCase(index.indexType())) {
            throw new IllegalArgumentException("Weaviate named Store currently supports hnsw indexes only");
        }
        if (!METRICS.contains(index.metric().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("unsupported Weaviate metric: " + index.metric());
        }
        if (index.shards() != 1) {
            throw new IllegalArgumentException("Weaviate shards are not applied by the current adapter");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "Weaviate index additional-fields");
        if (!index.indexParams().isEmpty()) {
            throw new IllegalArgumentException(
                    "Weaviate index-params are not applied by the current adapter and must be empty");
        }
        consistencyLevel(index);
        metadataFields(index);
    }

    @Override
    public VectorStoreCapabilities capabilities(
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        requireProvider(connection);
        return CAPABILITIES;
    }

    @Override
    public Set<String> physicalResourceIdentities(
            VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        return Set.of("class:" + boundIndex(store).name());
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        return new WeaviateConnectionHandle(definition.id(), client(definition.settings()));
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof WeaviateConnectionHandle weaviateConnection)
                || connection.provider() != VectorProvider.WEAVIATE
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("Weaviate factory received an incompatible connection handle");
        }
        VectorConnectionDefinition connectionDefinition = new VectorConnectionDefinition(
                connection.id(), provider(), Map.of());
        validateStore(connectionDefinition, definition);
        VectorIndexDefinition index = boundIndex(definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("Weaviate index dimension does not match the bound EmbeddingModel");
        }

        WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();
        options.setObjectClass(index.name());
        WeaviateVectorStore vectorStore = WeaviateVectorStore.builder(
                        weaviateConnection.client(), embeddingModel.model())
                .options(options)
                .consistencyLevel(consistencyLevel(index))
                .filterMetadataFields(metadataFields(index))
                .build();
        VectorFilterCompiler filterCompiler = new WeaviateVectorFilterCompiler();
        Map<String, String> managedClasses = Map.of(definition.defaultIndex(), index.name());
        WeaviateVectorServiceImpl service = new WeaviateVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                weaviateConnection.client(),
                filterCompiler,
                managedClasses);
        service.setVectorProperties(storeProperties(definition, index));
        service.setVectorFilterCompiler(filterCompiler);

        return VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(CAPABILITIES)
                .capability(VectorScoreSemantics.class, scoreSemantics(index.metric()))
                .capability(VectorFilterCompiler.class, filterCompiler)
                .capability(VectorAclAwareHybridSearchOperations.class, service)
                .capability(VectorHybridSearchOperations.class, service)
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service)
                .build();
    }

    static WeaviateClient client(Map<String, Object> settings) {
        Config config = new Config(
                text(settings, "scheme", "http"),
                text(settings, "host", "localhost:8080"),
                Map.of(),
                positiveInteger(settings, "connection-timeout-ms", 10_000),
                positiveInteger(settings, "connection-request-timeout-ms", 10_000),
                positiveInteger(settings, "socket-timeout-ms", 60_000));
        String apiKey = nullableText(settings, "api-key");
        if (apiKey == null) {
            return new WeaviateClient(config);
        }
        try {
            return WeaviateAuthClient.apiKey(config, apiKey);
        } catch (AuthException exception) {
            throw new IllegalStateException("Weaviate client authentication initialization failed", exception);
        }
    }

    private static VectorScoreSemantics scoreSemantics(String metric) {
        String lower = metric == null ? "cosine" : metric.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cosine" -> VectorScoreSemantics.finalScore(
                    "weaviate.provider.cosine", 0.0D, 1.0D, true, "weaviate-certainty",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "dot" -> VectorScoreSemantics.finalScore(
                    "weaviate.provider.dot", null, null, false, "weaviate-dot-score",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "l2-squared", "l2", "euclidean" -> VectorScoreSemantics.finalScore(
                    "weaviate.provider.euclidean", 0.0D, 1.0D, true, "1.0/(1.0+distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> VectorScoreSemantics.finalScore(
                    "weaviate.provider", null, null, false, "provider-query-score",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        };
    }

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException("Weaviate default index must identify the declared Store index");
        }
        return index;
    }

    private static WeaviateVectorStore.ConsistentLevel consistencyLevel(VectorIndexDefinition index) {
        String value = text(index.additionalFields(), "consistency-level", "QUORUM");
        try {
            return WeaviateVectorStore.ConsistentLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported Weaviate consistency-level: " + value);
        }
    }

    private static List<WeaviateVectorStore.MetadataField> metadataFields(VectorIndexDefinition index) {
        String configured = text(index.additionalFields(), "filter-metadata-fields", "");
        if (configured.isBlank()) return List.of();
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .map(pair -> {
                    String[] parts = pair.split(":", -1);
                    if (parts.length != 2 || !parts[0].matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        throw new IllegalArgumentException("invalid Weaviate filter-metadata-fields entry");
                    }
                    return switch (parts[1].toLowerCase(Locale.ROOT)) {
                        case "text" -> WeaviateVectorStore.MetadataField.text(parts[0]);
                        case "number" -> WeaviateVectorStore.MetadataField.number(parts[0]);
                        default -> throw new IllegalArgumentException(
                                "unsupported Weaviate metadata field type: " + parts[1]);
                    };
                })
                .toList();
    }

    private static VectorProperties storeProperties(
            VectorStoreDefinition store,
            VectorIndexDefinition index) {
        VectorProperties properties = new VectorProperties();
        properties.setDefaultIndex(store.defaultIndex());
        properties.setIndexes(Map.of(store.defaultIndex(), new VectorProperties.IndexConfig()
                .setName(index.name())
                .setDimension(index.dimension())
                .setMetric(index.metric())
                .setIndexType(index.indexType())
                .setReplicas(index.replicas())
                .setShards(index.shards())
                .setAdditionalFields(index.additionalFields())
                .setIndexParams(index.indexParams())));
        return properties;
    }

    private static void requireProvider(VectorConnectionDefinition definition) {
        if (definition.provider() != VectorProvider.WEAVIATE) {
            throw new IllegalArgumentException(
                    "Weaviate factory cannot handle provider " + definition.provider());
        }
    }

    private static void rejectUnknownKeys(Map<String, ?> values, Set<String> allowed, String field) {
        Set<String> unknown = new java.util.LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown " + field + ": " + unknown);
        }
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        if (text.isBlank() && !fallback.isEmpty()) {
            throw new IllegalArgumentException("Weaviate setting must not be blank: " + key);
        }
        return text;
    }

    private static String nullableText(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static int positiveInteger(Map<String, ?> values, String key, int fallback) {
        Object value = values.get(key);
        int result;
        if (value == null) {
            result = fallback;
        } else if (value instanceof Number number) {
            result = number.intValue();
        } else {
            try {
                result = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Weaviate setting must be an integer: " + key);
            }
        }
        if (result <= 0) {
            throw new IllegalArgumentException("Weaviate setting must be greater than zero: " + key);
        }
        return result;
    }

    private record WeaviateConnectionHandle(
            VectorConnectionId id,
            WeaviateClient client) implements VectorConnectionHandle {

        @Override
        public VectorProvider provider() {
            return VectorProvider.WEAVIATE;
        }

        @Override
        public void close() {
            // Weaviate Java client 5.x exposes no closeable transport handle.
        }

        @Override
        public String toString() {
            return "WeaviateConnectionHandle[id=" + id + ", provider=WEAVIATE, config=<redacted>]";
        }
    }
}
