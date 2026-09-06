/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.QdrantVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.VectorRecordReadOperations;
import cn.richie696.component.vector.service.impl.QdRantVectorServiceImpl;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorCapabilityDescriptor;
import cn.richie696.component.vector.topology.VectorCapabilityOperation;
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
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named Multi-store factory for Qdrant. */
public final class QdrantVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "host", "port", "use-transport-layer-security", "api-key", "timeout-ms");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of(
            "initialize-schema", "content-field-name", "payload-indexes");
    private static final Map<String, String> FILTER_CONSTRAINTS = Map.of(
            "filter-stage", "provider-recall",
            "transport", "qdrant-grpc-filter",
            "nodes", "EQ,IN,CONTAINS_ANY,RANGE,NOT,AND,OR",
            "equality-values", "string,int64");
    private static final VectorStoreCapabilities CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0",
                    Set.of(VectorCapabilityOperation.SEARCH_TEXT),
                    FILTER_CONSTRAINTS),
            new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0",
                    Set.of(VectorCapabilityOperation.SEARCH_TEXT),
                    FILTER_CONSTRAINTS),
            VectorCapabilityDescriptor.supported(VectorCapability.SCORE_STAGES),
            VectorCapabilityDescriptor.supported(VectorCapability.INDEX_LIFECYCLE)));

    private final RerankService rerankService;

    public QdrantVectorProviderFactory(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.QDRANT;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "Qdrant connection settings");
        text(definition.settings(), "host", "localhost");
        int port = integer(definition.settings(), "port", 6334);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Qdrant port must be between 1 and 65535");
        }
        booleanValue(definition.settings(), "use-transport-layer-security", false);
        nullableText(definition.settings(), "api-key");
        int timeout = integer(definition.settings(), "timeout-ms", 5_000);
        if (timeout <= 0) throw new IllegalArgumentException("Qdrant timeout-ms must be greater than zero");
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() != 1) {
            throw new IllegalArgumentException("Qdrant named Store requires exactly one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        if (!index.name().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,254}")) {
            throw new IllegalArgumentException("invalid Qdrant collection name");
        }
        if (!"hnsw".equalsIgnoreCase(index.indexType())) {
            throw new IllegalArgumentException("Qdrant named Store currently supports hnsw indexes only");
        }
        switch (index.metric().toLowerCase(Locale.ROOT)) {
            case "cosine", "l2", "euclidean", "ip", "dot" -> { }
            default -> throw new IllegalArgumentException("unsupported Qdrant metric: " + index.metric());
        }
        if (index.replicas() != 1 || index.shards() != 1) {
            throw new IllegalArgumentException("Qdrant replicas/shards are not applied by the current adapter");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "Qdrant index additional-fields");
        if (!index.indexParams().isEmpty()) {
            throw new IllegalArgumentException(
                    "Qdrant index-params are not applied by the current adapter and must be empty");
        }
        booleanValue(index.additionalFields(), "initialize-schema", false);
        text(index.additionalFields(), "content-field-name", "content");
        payloadIndexFields(index.additionalFields().get("payload-indexes"));
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
        return Set.of("collection:" + boundIndex(store).name());
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                text(definition.settings(), "host", "localhost"),
                integer(definition.settings(), "port", 6334),
                booleanValue(definition.settings(), "use-transport-layer-security", false),
                false);
        String apiKey = nullableText(definition.settings(), "api-key");
        if (apiKey != null) builder.withApiKey(apiKey);
        builder.withTimeout(Duration.ofMillis(integer(definition.settings(), "timeout-ms", 5_000)));
        return new QdrantConnectionHandle(definition.id(), new QdrantClient(builder.build()));
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof QdrantConnectionHandle qdrantConnection)
                || connection.provider() != VectorProvider.QDRANT
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("Qdrant factory received an incompatible connection handle");
        }
        validateStore(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition);
        VectorIndexDefinition index = boundIndex(definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("Qdrant index dimension does not match the bound EmbeddingModel");
        }
        QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantConnection.client(), embeddingModel.model())
                .collectionName(index.name())
                .contentFieldName(text(index.additionalFields(), "content-field-name", "content"))
                .initializeSchema(booleanValue(index.additionalFields(), "initialize-schema", false))
                .build();
        if (booleanValue(index.additionalFields(), "initialize-schema", false)) {
            try {
                vectorStore.afterPropertiesSet();
            } catch (Exception exception) {
                throw new IllegalStateException("Qdrant named Store schema initialization failed", exception);
            }
        }
        QdRantVectorServiceImpl service = new QdRantVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                qdrantConnection.client(),
                Map.of(definition.defaultIndex(), index.name()));
        VectorFilterCompiler filterCompiler = new QdrantVectorFilterCompiler();
        service.setVectorProperties(storeProperties(definition, index));
        service.setVectorFilterCompiler(filterCompiler);
        List<cn.richie696.component.vector.service.VectorPayloadIndexOperations.FieldDefinition> payloadFields =
                payloadIndexFields(index.additionalFields().get("payload-indexes"));
        VectorStoreHandle.Builder handle = VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(CAPABILITIES)
                .capability(VectorScoreSemantics.class, scoreSemantics(index.metric()))
                .capability(VectorFilterCompiler.class, filterCompiler)
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service);
        if (!payloadFields.isEmpty()) {
            handle.capability(cn.richie696.component.vector.service.VectorPayloadIndexOperations.class, service);
        }
        VectorStoreHandle built = handle.build();
        if (booleanValue(index.additionalFields(), "initialize-schema", false) && !payloadFields.isEmpty()) {
            try {
                service.createPayloadIndexes(index.name(), payloadFields);
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Qdrant named Store payload index initialization failed", exception);
            }
        }
        return built;
    }

    private static List<cn.richie696.component.vector.service.VectorPayloadIndexOperations.FieldDefinition> payloadIndexFields(Object configured) {
        if (configured == null) return List.of();
        String text = String.valueOf(configured).trim();
        if (text.isBlank()) return List.of();
        List<cn.richie696.component.vector.service.VectorPayloadIndexOperations.FieldDefinition> result = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (String item : text.split(";")) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("Qdrant payload-indexes entries must not be empty");
            }
            String[] parts = entry.split(":", -1);
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException(
                        "Qdrant payload-indexes entries must use field:TYPE[:settings]");
            }
            String field = parts[0].trim();
            if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("invalid Qdrant payload field: " + field);
            }
            if (!names.add(field)) {
                throw new IllegalArgumentException("duplicate Qdrant payload field: " + field);
            }
            cn.richie696.component.vector.service.VectorPayloadIndexOperations.FieldType type;
            try {
                type = cn.richie696.component.vector.service.VectorPayloadIndexOperations.FieldType
                        .valueOf(parts[1].trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unsupported Qdrant payload field type: " + parts[1]);
            }
            Map<String, String> settings = parts.length == 3 && !parts[2].isBlank()
                    ? Map.of(parts[2].trim(), "true") : Map.of();
            result.add(new cn.richie696.component.vector.service.VectorPayloadIndexOperations
                    .FieldDefinition(field, type, settings));
        }
        return List.copyOf(result);
    }

    private static VectorScoreSemantics scoreSemantics(String metric) {
        String lower = metric == null ? "cosine" : metric.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cosine" -> VectorScoreSemantics.finalScore(
                    "qdrant.provider.cosine", 0.0D, 1.0D, true, "qdrant-similarity-score",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "ip", "dot" -> VectorScoreSemantics.finalScore(
                    "qdrant.provider.inner-product", null, null, false, "qdrant-raw-dot",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "l2", "euclidean" -> VectorScoreSemantics.finalScore(
                    "qdrant.provider.euclidean", 0.0D, 1.0D, true, "qdrant-normalized-distance",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> VectorScoreSemantics.finalScore(
                    "qdrant.provider", null, null, false, "provider-defined-metric-score",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        };
    }

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException("Qdrant default index must identify the declared Store index");
        }
        return index;
    }

    private static VectorProperties storeProperties(VectorStoreDefinition store, VectorIndexDefinition index) {
        VectorProperties properties = new VectorProperties();
        properties.setDefaultIndex(store.defaultIndex());
        properties.setIndexes(Map.of(store.defaultIndex(), new VectorProperties.IndexConfig()
                .setName(index.name()).setDimension(index.dimension()).setMetric(index.metric())
                .setIndexType(index.indexType()).setReplicas(index.replicas()).setShards(index.shards())
                .setAdditionalFields(index.additionalFields()).setIndexParams(index.indexParams())));
        return properties;
    }

    private static void requireProvider(VectorConnectionDefinition definition) {
        if (definition.provider() != VectorProvider.QDRANT) {
            throw new IllegalArgumentException("Qdrant factory cannot handle provider " + definition.provider());
        }
    }

    private static void rejectUnknownKeys(Map<String, ?> values, Set<String> allowed, String field) {
        Set<String> unknown = new java.util.LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) throw new IllegalArgumentException("unknown " + field + ": " + unknown);
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        String result = String.valueOf(value).trim();
        if (result.isBlank()) throw new IllegalArgumentException("Qdrant setting must not be blank: " + key);
        return result;
    }

    private static String nullableText(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        return result.isBlank() ? null : result;
    }

    private static int integer(Map<String, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Qdrant setting must be an integer: " + key);
        }
    }

    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("Qdrant setting must be a boolean: " + key);
    }

    private record QdrantConnectionHandle(
            VectorConnectionId id,
            QdrantClient client) implements VectorConnectionHandle {

        @Override
        public VectorProvider provider() {
            return VectorProvider.QDRANT;
        }

        @Override
        public void close() {
            client.close();
        }

        @Override
        public String toString() {
            return "QdrantConnectionHandle[id=" + id + ", provider=QDRANT, config=<redacted>]";
        }
    }
}
