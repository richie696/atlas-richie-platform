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
import cn.richie696.component.vector.service.impl.Neo4jVectorServiceImpl;
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
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Named Multi-store factory for Neo4j vector indexes. */
public final class Neo4jVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "uri", "username", "password", "database", "max-connection-pool-size",
            "max-connection-lifetime-ms", "connection-acquisition-timeout-ms",
            "connection-timeout-ms", "max-transaction-retry-time-ms", "encryption-enabled", "user-agent");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of("initialize-schema");
    private static final VectorStoreCapabilities CAPABILITIES = VectorStoreCapabilities.of(
            VectorCapability.NATIVE_FILTER,
            VectorCapability.SCORE_STAGES,
            VectorCapability.INDEX_LIFECYCLE);

    private final RerankService rerankService;
    private final Function<VectorConnectionDefinition, Driver> driverFactory;

    public Neo4jVectorProviderFactory(RerankService rerankService) {
        this(rerankService, Neo4jVectorProviderFactory::createDriver);
    }

    Neo4jVectorProviderFactory(
            RerankService rerankService,
            Function<VectorConnectionDefinition, Driver> driverFactory) {
        this.rerankService = rerankService;
        this.driverFactory = driverFactory;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.NEO4J;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "Neo4j connection settings");
        String uri = text(definition.settings(), "uri", "bolt://localhost:7687");
        if (!uri.matches("^(bolt|bolt\\+s|bolt\\+ssc|neo4j|neo4j\\+s|neo4j\\+ssc)://.+")) {
            throw new IllegalArgumentException("Neo4j uri must use a supported bolt or neo4j scheme");
        }
        String username = nullableText(definition.settings(), "username");
        String password = nullableText(definition.settings(), "password");
        if ((username == null) != (password == null)) {
            throw new IllegalArgumentException("Neo4j username and password must be configured together");
        }
        identifier(text(definition.settings(), "database", "neo4j"), "database");
        positiveInteger(definition.settings(), "max-connection-pool-size", 50);
        positiveLong(definition.settings(), "max-connection-lifetime-ms", 3_600_000L);
        positiveLong(definition.settings(), "connection-acquisition-timeout-ms", 10_000L);
        positiveLong(definition.settings(), "connection-timeout-ms", 5_000L);
        positiveLong(definition.settings(), "max-transaction-retry-time-ms", 15_000L);
        booleanValue(definition.settings(), "encryption-enabled", false);
        text(definition.settings(), "user-agent", "atlas-richie-vector");
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() != 1) {
            throw new IllegalArgumentException("Neo4j named Store requires exactly one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        identifier(index.name(), "physical index base");
        if (!"hnsw".equalsIgnoreCase(index.indexType())) {
            throw new IllegalArgumentException("Neo4j named Store currently supports hnsw indexes only");
        }
        switch (index.metric().toLowerCase(Locale.ROOT)) {
            case "cosine", "l2", "euclidean" -> { }
            default -> throw new IllegalArgumentException("unsupported Neo4j metric: " + index.metric());
        }
        if (index.replicas() != 1 || index.shards() != 1) {
            throw new IllegalArgumentException("Neo4j replicas/shards are not applied by the current adapter");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "Neo4j index additional-fields");
        if (!index.indexParams().isEmpty()) {
            throw new IllegalArgumentException(
                    "Neo4j index-params are not applied by the current adapter and must be empty");
        }
        booleanValue(index.additionalFields(), "initialize-schema", false);
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
        VectorIndexDefinition index = boundIndex(store);
        return Set.of(
                "label:VectorDocument_" + index.name(),
                "vector-index:" + index.name() + "_idx");
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        return new Neo4jConnectionHandle(definition.id(), driverFactory.apply(definition),
                SessionConfig.forDatabase(text(definition.settings(), "database", "neo4j")));
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof Neo4jConnectionHandle neo4jConnection)
                || connection.provider() != VectorProvider.NEO4J
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("Neo4j factory received an incompatible connection handle");
        }
        validateStore(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition);
        VectorIndexDefinition index = boundIndex(definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("Neo4j index dimension does not match the bound EmbeddingModel");
        }
        String label = "VectorDocument_" + index.name();
        String vectorIndex = index.name() + "_idx";
        boolean initializeSchema = booleanValue(index.additionalFields(), "initialize-schema", false);
        Neo4jVectorStore vectorStore = Neo4jVectorStore.builder(neo4jConnection.driver(), embeddingModel.model())
                .sessionConfig(neo4jConnection.sessionConfig())
                .databaseName(neo4jConnection.sessionConfig().database().orElse("neo4j"))
                .embeddingDimension(index.dimension())
                .distanceType(distanceType(index.metric()))
                .label(label)
                .indexName(vectorIndex)
                .constraintName(index.name() + "_id_unique")
                .embeddingProperty("embedding")
                .idProperty("id")
                .textProperty("content")
                .initializeSchema(initializeSchema)
                .build();
        if (initializeSchema) vectorStore.afterPropertiesSet();
        VectorFilterCompiler filterCompiler = new SpringAiVectorFilterCompiler();
        Neo4jVectorServiceImpl service = new Neo4jVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                neo4jConnection.driver(),
                neo4jConnection.sessionConfig(),
                Map.of(definition.defaultIndex(), index.name()));
        service.setVectorProperties(storeProperties(definition, index));
        service.setVectorFilterCompiler(filterCompiler);
        return VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(CAPABILITIES)
                .capability(VectorScoreSemantics.class, scoreSemantics(index.metric()))
                .capability(VectorFilterCompiler.class, filterCompiler)
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service)
                .build();
    }

    private static Driver createDriver(VectorConnectionDefinition definition) {
        Config.ConfigBuilder config = Config.builder()
                .withMaxConnectionPoolSize(integer(definition.settings(), "max-connection-pool-size", 50))
                .withMaxConnectionLifetime(longValue(
                        definition.settings(), "max-connection-lifetime-ms", 3_600_000L), TimeUnit.MILLISECONDS)
                .withConnectionAcquisitionTimeout(longValue(
                        definition.settings(), "connection-acquisition-timeout-ms", 10_000L), TimeUnit.MILLISECONDS)
                .withConnectionTimeout(longValue(
                        definition.settings(), "connection-timeout-ms", 5_000L), TimeUnit.MILLISECONDS)
                .withMaxTransactionRetryTime(longValue(
                        definition.settings(), "max-transaction-retry-time-ms", 15_000L), TimeUnit.MILLISECONDS)
                .withUserAgent(text(definition.settings(), "user-agent", "atlas-richie-vector"));
        if (booleanValue(definition.settings(), "encryption-enabled", false)) {
            config.withEncryption().withTrustStrategy(Config.TrustStrategy.trustSystemCertificates());
        } else {
            config.withoutEncryption();
        }
        String username = nullableText(definition.settings(), "username");
        AuthToken auth = username == null
                ? AuthTokens.none()
                : AuthTokens.basic(username, nullableText(definition.settings(), "password"));
        return GraphDatabase.driver(text(definition.settings(), "uri", "bolt://localhost:7687"), auth, config.build());
    }

    private static Neo4jVectorStore.Neo4jDistanceType distanceType(String metric) {
        return "cosine".equalsIgnoreCase(metric)
                ? Neo4jVectorStore.Neo4jDistanceType.COSINE
                : Neo4jVectorStore.Neo4jDistanceType.EUCLIDEAN;
    }

    private static VectorScoreSemantics scoreSemantics(String metric) {
        String lower = metric == null ? "cosine" : metric.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cosine" -> VectorScoreSemantics.finalScore(
                    "neo4j.provider.cosine", 0.0D, 1.0D, true, "1.0-distance",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "l2", "euclidean" -> VectorScoreSemantics.finalScore(
                    "neo4j.provider.euclidean", 0.0D, 1.0D, true, "1.0/(1.0+distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> VectorScoreSemantics.finalScore(
                    "neo4j.provider", null, null, false, "provider-vector-query-score",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        };
    }

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException("Neo4j default index must identify the declared Store index");
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
        if (definition.provider() != VectorProvider.NEO4J) {
            throw new IllegalArgumentException("Neo4j factory cannot handle provider " + definition.provider());
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
        if (result.isBlank()) throw new IllegalArgumentException("Neo4j setting must not be blank: " + key);
        return result;
    }

    private static String nullableText(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        return result.isBlank() ? null : result;
    }

    private static int integer(Map<String, ?> values, String key, int fallback) {
        long value = longValue(values, key, fallback);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Neo4j setting is too large: " + key);
        return (int) value;
    }

    private static long longValue(Map<String, ?> values, String key, long fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Neo4j setting must be an integer: " + key);
        }
    }

    private static int positiveInteger(Map<String, ?> values, String key, int fallback) {
        int value = integer(values, key, fallback);
        if (value <= 0) throw new IllegalArgumentException("Neo4j setting must be greater than zero: " + key);
        return value;
    }

    private static long positiveLong(Map<String, ?> values, String key, long fallback) {
        long value = longValue(values, key, fallback);
        if (value <= 0) throw new IllegalArgumentException("Neo4j setting must be greater than zero: " + key);
        return value;
    }

    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("Neo4j setting must be a boolean: " + key);
    }

    private static String identifier(String value, String field) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("invalid Neo4j " + field);
        }
        return value;
    }

    private record Neo4jConnectionHandle(
            VectorConnectionId id,
            Driver driver,
            SessionConfig sessionConfig) implements VectorConnectionHandle {

        @Override
        public VectorProvider provider() {
            return VectorProvider.NEO4J;
        }

        @Override
        public void close() {
            driver.close();
        }

        @Override
        public String toString() {
            return "Neo4jConnectionHandle[id=" + id + ", provider=NEO4J, config=<redacted>]";
        }
    }
}
