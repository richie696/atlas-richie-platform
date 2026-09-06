/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.VectorRecordReadOperations;
import cn.richie696.component.vector.service.impl.RedisVectorServiceImpl;
import cn.richie696.component.vector.topology.VectorCapability;
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
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named Multi-store factory for Redis Stack. */
public final class RedisVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "host", "port", "ssl", "username", "password", "database", "client-name",
            "connection-timeout-ms", "socket-timeout-ms", "blocking-socket-timeout-ms");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of(
            "initialize-schema", "metadata-fields");
    private static final Set<String> INDEX_PARAM_KEYS = Set.of("M", "efConstruction", "efRuntime");
    private static final VectorStoreCapabilities BASE_CAPABILITIES = VectorStoreCapabilities.of(
            VectorCapability.SCORE_STAGES,
            VectorCapability.INDEX_LIFECYCLE);
    private static final VectorStoreCapabilities ADAPTER_CAPABILITIES = VectorStoreCapabilities.of(
            VectorCapability.NATIVE_FILTER,
            VectorCapability.ACL_FILTER,
            VectorCapability.SCORE_STAGES,
            VectorCapability.INDEX_LIFECYCLE);

    private final RerankService rerankService;

    public RedisVectorProviderFactory(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.REDIS;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return ADAPTER_CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "Redis connection settings");
        text(definition.settings(), "host", "localhost");
        int port = integer(definition.settings(), "port", 6379);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Redis port must be between 1 and 65535");
        }
        booleanValue(definition.settings(), "ssl", false);
        nullableText(definition.settings(), "username");
        nullableText(definition.settings(), "password");
        text(definition.settings(), "client-name", "richie-vector-client");
        int database = integer(definition.settings(), "database", 0);
        if (database < 0) throw new IllegalArgumentException("Redis database must not be negative");
        positiveInteger(definition.settings(), "connection-timeout-ms", 2_000);
        positiveInteger(definition.settings(), "socket-timeout-ms", 2_000);
        positiveInteger(definition.settings(), "blocking-socket-timeout-ms", 2_000);
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() != 1) {
            throw new IllegalArgumentException("Redis named Store requires exactly one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        if (!index.name().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("invalid Redis index name");
        }
        switch (index.indexType().toLowerCase(Locale.ROOT)) {
            case "hnsw", "flat" -> { }
            default -> throw new IllegalArgumentException("unsupported Redis index type: " + index.indexType());
        }
        switch (index.metric().toLowerCase(Locale.ROOT)) {
            case "cosine", "l2", "euclidean", "ip", "dot" -> { }
            default -> throw new IllegalArgumentException("unsupported Redis metric: " + index.metric());
        }
        if (index.replicas() != 1 || index.shards() != 1) {
            throw new IllegalArgumentException("Redis replicas/shards are not applied by the current adapter");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "Redis index additional-fields");
        rejectUnknownKeys(index.indexParams(), INDEX_PARAM_KEYS, "Redis index-params");
        booleanValue(index.additionalFields(), "initialize-schema", false);
        metadataFields(index.additionalFields().get("metadata-fields"));
        if ("flat".equalsIgnoreCase(index.indexType()) && !index.indexParams().isEmpty()) {
            throw new IllegalArgumentException("Redis HNSW index-params cannot be used with a flat index");
        }
        index.indexParams().forEach((key, value) -> positiveInteger(index.indexParams(), key, 1));
    }

    @Override
    public VectorStoreCapabilities capabilities(
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        requireProvider(connection);
        return storeCapabilities(boundIndex(store));
    }

    @Override
    public Set<String> physicalResourceIdentities(
            VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        VectorIndexDefinition index = boundIndex(store);
        return Set.of("search-index:" + index.name(), "key-prefix:" + index.name() + ":");
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        var clientConfig = DefaultJedisClientConfig.builder()
                .ssl(booleanValue(definition.settings(), "ssl", false))
                .database(integer(definition.settings(), "database", 0))
                .clientName(text(definition.settings(), "client-name", "richie-vector-client"))
                .connectionTimeoutMillis(integer(definition.settings(), "connection-timeout-ms", 2_000))
                .socketTimeoutMillis(integer(definition.settings(), "socket-timeout-ms", 2_000))
                .blockingSocketTimeoutMillis(integer(
                        definition.settings(), "blocking-socket-timeout-ms", 2_000));
        String username = nullableText(definition.settings(), "username");
        String password = nullableText(definition.settings(), "password");
        if (username != null) clientConfig.user(username);
        if (password != null) clientConfig.password(password);
        RedisClient client = RedisClient.builder()
                .hostAndPort(text(definition.settings(), "host", "localhost"),
                        integer(definition.settings(), "port", 6379))
                .clientConfig(clientConfig.build())
                .build();
        return new RedisConnectionHandle(definition.id(), client);
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof RedisConnectionHandle redisConnection)
                || connection.provider() != VectorProvider.REDIS
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("Redis factory received an incompatible connection handle");
        }
        validateStore(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition);
        VectorIndexDefinition index = boundIndex(definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("Redis index dimension does not match the bound EmbeddingModel");
        }

        RedisVectorStore.Builder storeBuilder = RedisVectorStore.builder(redisConnection.client(), embeddingModel.model())
                .indexName(index.name())
                .prefix(index.name() + ":")
                .contentFieldName("content")
                .embeddingFieldName("embedding")
                .vectorAlgorithm(redisAlgorithm(index.indexType()))
                .distanceMetric(redisMetric(index.metric()))
                .metadataFields(metadataFields(index.additionalFields().get("metadata-fields")))
                .initializeSchema(booleanValue(index.additionalFields(), "initialize-schema", false));
        if ("hnsw".equalsIgnoreCase(index.indexType())) {
            applyHnswParams(storeBuilder, index.indexParams());
        }
        RedisVectorStore vectorStore = storeBuilder.build();
        if (booleanValue(index.additionalFields(), "initialize-schema", false)) {
            vectorStore.afterPropertiesSet();
        }
        VectorFilterCompiler filterCompiler = new SpringAiVectorFilterCompiler();
        VectorStoreCapabilities capabilities = storeCapabilities(index);
        RedisVectorServiceImpl service = new RedisVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                Map.of(definition.defaultIndex(), index.name()));
        service.setVectorProperties(storeProperties(definition, index));
        VectorStoreHandle.Builder handle = VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(capabilities)
                .capability(VectorScoreSemantics.class, scoreSemantics(index.metric()))
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service);
        if (capabilities.supports(VectorCapability.NATIVE_FILTER)) {
            service.setVectorFilterCompiler(filterCompiler);
            handle.capability(VectorFilterCompiler.class, filterCompiler);
        }
        return handle.build();
    }

    private static VectorScoreSemantics scoreSemantics(String metric) {
        String lower = metric == null ? "cosine" : metric.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cosine" -> VectorScoreSemantics.finalScore(
                    "redis.adapter.cosine", 0.0D, 1.0D, true, "max(0,1-distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "ip", "dot" -> VectorScoreSemantics.finalScore(
                    "redis.adapter.inner-product", null, null, false, "raw-inner-product",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "l2", "euclidean" -> VectorScoreSemantics.finalScore(
                    "redis.adapter.euclidean", 0.0D, 1.0D, true, "max(0,1-distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> VectorScoreSemantics.finalScore(
                    "redis.adapter", 0.0D, 1.0D, true, "max(0,1-distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        };
    }

    private static VectorStoreCapabilities storeCapabilities(VectorIndexDefinition index) {
        return metadataFields(index.additionalFields().get("metadata-fields")).isEmpty()
                ? BASE_CAPABILITIES
                : VectorStoreCapabilities.of(
                        VectorCapability.NATIVE_FILTER,
                        VectorCapability.ACL_FILTER,
                        VectorCapability.SCORE_STAGES,
                        VectorCapability.INDEX_LIFECYCLE);
    }

    private static void applyHnswParams(RedisVectorStore.Builder builder, Map<String, Object> params) {
        if (params.containsKey("M")) builder.hnswM(integer(params, "M", 16));
        if (params.containsKey("efConstruction")) {
            builder.hnswEfConstruction(integer(params, "efConstruction", 200));
        }
        if (params.containsKey("efRuntime")) builder.hnswEfRuntime(integer(params, "efRuntime", 10));
    }

    private static RedisVectorStore.Algorithm redisAlgorithm(String indexType) {
        return "flat".equalsIgnoreCase(indexType)
                ? RedisVectorStore.Algorithm.FLAT : RedisVectorStore.Algorithm.HNSW;
    }

    private static RedisVectorStore.DistanceMetric redisMetric(String metric) {
        return switch (metric.toLowerCase(Locale.ROOT)) {
            case "l2", "euclidean" -> RedisVectorStore.DistanceMetric.L2;
            case "ip", "dot" -> RedisVectorStore.DistanceMetric.IP;
            default -> RedisVectorStore.DistanceMetric.COSINE;
        };
    }

    private static List<RedisVectorStore.MetadataField> metadataFields(Object configured) {
        if (configured == null) return List.of();
        String text = String.valueOf(configured).trim();
        if (text.isBlank()) return List.of();
        List<RedisVectorStore.MetadataField> result = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (String item : text.split(",")) {
            String[] parts = item.trim().split(":", -1);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException(
                        "Redis metadata-fields must use field:text|tag|numeric entries");
            }
            if (!names.add(parts[0])) {
                throw new IllegalArgumentException("duplicate Redis metadata field: " + parts[0]);
            }
            result.add(switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "text" -> RedisVectorStore.MetadataField.text(parts[0]);
                case "tag" -> RedisVectorStore.MetadataField.tag(parts[0]);
                case "numeric", "number" -> RedisVectorStore.MetadataField.numeric(parts[0]);
                default -> throw new IllegalArgumentException(
                        "unsupported Redis metadata field type: " + parts[1]);
            });
        }
        return List.copyOf(result);
    }

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException("Redis default index must identify the declared Store index");
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
        if (definition.provider() != VectorProvider.REDIS) {
            throw new IllegalArgumentException("Redis factory cannot handle provider " + definition.provider());
        }
    }

    private static void rejectUnknownKeys(Map<String, ?> values, Set<String> allowed, String field) {
        Set<String> unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) throw new IllegalArgumentException("unknown " + field + ": " + unknown);
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        String result = String.valueOf(value).trim();
        if (result.isBlank()) throw new IllegalArgumentException("Redis setting must not be blank: " + key);
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
            throw new IllegalArgumentException("Redis setting must be an integer: " + key);
        }
    }

    private static int positiveInteger(Map<String, ?> values, String key, int fallback) {
        int value = integer(values, key, fallback);
        if (value <= 0) throw new IllegalArgumentException("Redis setting must be greater than zero: " + key);
        return value;
    }

    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("Redis setting must be a boolean: " + key);
    }

    private record RedisConnectionHandle(
            VectorConnectionId id,
            RedisClient client) implements VectorConnectionHandle {

        @Override
        public VectorProvider provider() {
            return VectorProvider.REDIS;
        }

        @Override
        public void close() {
            client.close();
        }

        @Override
        public String toString() {
            return "RedisConnectionHandle[id=" + id + ", provider=REDIS, config=<redacted>]";
        }
    }
}
