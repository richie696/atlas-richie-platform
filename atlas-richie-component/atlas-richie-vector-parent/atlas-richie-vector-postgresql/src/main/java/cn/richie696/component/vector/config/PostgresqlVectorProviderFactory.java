/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.query.VectorQueryDefaults;
import cn.richie696.component.vector.service.PrecomputedVectorOperations;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.VectorRecordReadOperations;
import cn.richie696.component.vector.service.impl.PostgresqlAdvancedSearchOperations;
import cn.richie696.component.vector.service.impl.PostgresqlPrecomputedVectorOperations;
import cn.richie696.component.vector.service.impl.PostgresqlVectorServiceImpl;
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
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named Multi-store factory for PostgreSQL/pgvector. */
public final class PostgresqlVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "jdbc-url", "username", "password", "maximum-pool-size", "minimum-idle",
            "idle-timeout-ms", "max-lifetime-ms", "connection-timeout-ms", "validation-timeout-ms",
            "pool-name", "auto-commit", "connection-test-query");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of(
            "schema-name", "initialize-schema", "vector-table-validations-enabled", "max-document-batch-size");
    private static final VectorStoreCapabilities CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0",
                    Map.of("api", PrecomputedVectorOperations.class.getName(), "filter-stage", "provider-recall")),
            new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0",
                    Map.of("api", PrecomputedVectorOperations.class.getName(), "filter-stage", "provider-recall")),
            new VectorCapabilityDescriptor(VectorCapability.CANDIDATE_VECTOR, "1.0",
                    Map.of("api", VectorAdvancedSearchOperations.class.getName(),
                            "default", "disabled", "client-mmr", "true", "server-mmr", "false")),
            new VectorCapabilityDescriptor(VectorCapability.QUERY_TUNING, "1.0",
                    Map.of("api", VectorAdvancedSearchOperations.class.getName(),
                            "scope", "transaction", "supported", "hnsw.ef_search,ivfflat.probes")),
            VectorCapabilityDescriptor.supported(VectorCapability.SCORE_STAGES),
            VectorCapabilityDescriptor.supported(VectorCapability.INDEX_LIFECYCLE),
            VectorCapabilityDescriptor.supported(VectorCapability.PRECOMPUTED_VECTOR)));

    private final RerankService rerankService;

    public PostgresqlVectorProviderFactory(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.POSTGRESQL;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "PostgreSQL connection settings");
        String jdbcUrl = text(definition.settings(), "jdbc-url", "jdbc:postgresql://localhost:5432/yourdb");
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("PostgreSQL jdbc-url must use the jdbc:postgresql scheme");
        }
        int maximumPoolSize = integer(definition.settings(), "maximum-pool-size", 30);
        int minimumIdle = integer(definition.settings(), "minimum-idle", 10);
        if (maximumPoolSize <= 0 || minimumIdle < 0 || minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException(
                    "PostgreSQL pool sizes require maximum-pool-size > 0 and 0 <= minimum-idle <= maximum-pool-size");
        }
        positiveLong(definition.settings(), "idle-timeout-ms", 600_000L);
        positiveLong(definition.settings(), "max-lifetime-ms", 1_800_000L);
        positiveLong(definition.settings(), "connection-timeout-ms", 30_000L);
        positiveLong(definition.settings(), "validation-timeout-ms", 5_000L);
        booleanValue(definition.settings(), "auto-commit", true);
        nullableText(definition.settings(), "username");
        nullableText(definition.settings(), "password");
        text(definition.settings(), "pool-name", "VectorPg");
        text(definition.settings(), "connection-test-query", "SELECT 1");
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() > 1) {
            throw new IllegalArgumentException("PostgreSQL named Store currently supports one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        requireIdentifier(index.name(), "table name");
        requireIdentifier(schemaName(index), "schema name");
        distanceType(index.metric());
        indexType(index.indexType());
        if (index.replicas() != 1 || index.shards() != 1) {
            throw new IllegalArgumentException("PostgreSQL named Store does not apply replicas or shards");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "PostgreSQL index additional-fields");
        if (!index.indexParams().isEmpty()) {
            throw new IllegalArgumentException(
                    "PostgreSQL index-params are not applied by the current adapter and must be empty");
        }
        booleanValue(index.additionalFields(), "initialize-schema", false);
        booleanValue(index.additionalFields(), "vector-table-validations-enabled", true);
        int batchSize = integer(index.additionalFields(), "max-document-batch-size", 10_000);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("PostgreSQL max-document-batch-size must be greater than zero");
        }
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
        return Set.of("table:" + schemaName(index) + "." + physicalTable(index.name()));
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        HikariDataSource dataSource = dataSource(definition);
        return new PostgresqlConnectionHandle(definition.id(), dataSource, new JdbcTemplate(dataSource));
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof PostgresqlConnectionHandle postgresqlConnection)
                || connection.provider() != VectorProvider.POSTGRESQL
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("PostgreSQL factory received an incompatible connection handle");
        }
        VectorConnectionDefinition connectionDefinition = new VectorConnectionDefinition(
                connection.id(), provider(), Map.of());
        validateStore(connectionDefinition, definition);
        VectorIndexDefinition index = definition.indexes().isEmpty() && embeddingModel.dimensions() > 0
                ? defaultIndex(definition, embeddingModel.dimensions())
                : boundIndex(definition);
        if (!definition.indexes().isEmpty()
                && embeddingModel.dimensions() > 0
                && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("PostgreSQL index dimension does not match the bound EmbeddingModel");
        }

        String schema = schemaName(index);
        String table = physicalTable(index.name());
        PgVectorStore vectorStore = PgVectorStore.builder(postgresqlConnection.jdbcTemplate(), embeddingModel.model())
                .schemaName(schema)
                .vectorTableName(table)
                .dimensions(index.dimension())
                .distanceType(distanceType(index.metric()))
                .indexType(indexType(index.indexType()))
                .initializeSchema(booleanValue(index.additionalFields(), "initialize-schema", false))
                .vectorTableValidationsEnabled(booleanValue(
                        index.additionalFields(), "vector-table-validations-enabled", true))
                .maxDocumentBatchSize(integer(index.additionalFields(), "max-document-batch-size", 10_000))
                .build();

        Map<String, String> managedTables = Map.of(definition.defaultIndex(), table);
        PostgresqlVectorServiceImpl service = new PostgresqlVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                postgresqlConnection.jdbcTemplate(),
                schema,
                managedTables);
        service.setVectorProperties(storeProperties(definition, index));
        PostgresqlPrecomputedVectorOperations precomputed = new PostgresqlPrecomputedVectorOperations(
                postgresqlConnection.jdbcTemplate(), schema, managedTables);
        VectorAdvancedSearchOperations advancedSearch = new PostgresqlAdvancedSearchOperations(
                service,
                definition.id(),
                definition.defaultIndex(),
                index.indexType(),
                (VectorQueryDefaults) null,
                definition.queryDefaults(),
                scoreSemantics(index.metric()));

        return VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(CAPABILITIES)
                .capability(VectorScoreSemantics.class, scoreSemantics(index.metric()))
                .capability(PrecomputedVectorOperations.class, precomputed)
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service)
                .capability(VectorAdvancedSearchOperations.class, advancedSearch)
                .build();
    }

    private static HikariDataSource dataSource(VectorConnectionDefinition definition) {
        Map<String, Object> settings = definition.settings();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(text(settings, "jdbc-url", "jdbc:postgresql://localhost:5432/yourdb"));
        dataSource.setUsername(nullableText(settings, "username"));
        dataSource.setPassword(nullableText(settings, "password"));
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(integer(settings, "maximum-pool-size", 30));
        dataSource.setMinimumIdle(integer(settings, "minimum-idle", 10));
        dataSource.setIdleTimeout(positiveLong(settings, "idle-timeout-ms", 600_000L));
        dataSource.setMaxLifetime(positiveLong(settings, "max-lifetime-ms", 1_800_000L));
        dataSource.setConnectionTimeout(positiveLong(settings, "connection-timeout-ms", 30_000L));
        dataSource.setValidationTimeout(positiveLong(settings, "validation-timeout-ms", 5_000L));
        dataSource.setPoolName(text(settings, "pool-name", "VectorPg") + "-" + definition.id().value());
        dataSource.setAutoCommit(booleanValue(settings, "auto-commit", true));
        dataSource.setConnectionTestQuery(text(settings, "connection-test-query", "SELECT 1"));
        return dataSource;
    }

    private static VectorProperties storeProperties(VectorStoreDefinition store, VectorIndexDefinition index) {
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

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        if (store.indexes().isEmpty()) {
            return defaultIndex(store, 1536);
        }
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException("PostgreSQL default index must identify the declared Store index");
        }
        return index;
    }

    private static VectorIndexDefinition defaultIndex(VectorStoreDefinition store, int dimension) {
        return new VectorIndexDefinition(
                store.defaultIndex(), store.defaultIndex(), dimension, "cosine", "hnsw", 1, 1, Map.of(), Map.of());
    }

    private static String schemaName(VectorIndexDefinition index) {
        return text(index.additionalFields(), "schema-name", "public");
    }

    private static String physicalTable(String physicalIndexName) {
        return "vector_" + physicalIndexName;
    }

    private static PgVectorStore.PgDistanceType distanceType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "cosine" -> PgVectorStore.PgDistanceType.COSINE_DISTANCE;
            case "l2", "euclidean" -> PgVectorStore.PgDistanceType.EUCLIDEAN_DISTANCE;
            case "ip", "dot" -> PgVectorStore.PgDistanceType.NEGATIVE_INNER_PRODUCT;
            default -> throw new IllegalArgumentException("unsupported PostgreSQL metric: " + value);
        };
    }

    private static PgVectorStore.PgIndexType indexType(String value) {
        try {
            return PgVectorStore.PgIndexType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported PostgreSQL index type: " + value);
        }
    }

    private static VectorScoreSemantics scoreSemantics(String metric) {
        String lower = metric == null ? "cosine" : metric.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cosine" -> VectorScoreSemantics.finalScore(
                    "postgresql.adapter.cosine", 0.0D, 1.0D, true, "1.0-distance",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "l2", "euclidean" -> VectorScoreSemantics.finalScore(
                    "postgresql.adapter.euclidean", 0.0D, 1.0D, true, "1.0/(1.0+distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "ip", "dot" -> VectorScoreSemantics.finalScore(
                    "postgresql.adapter.inner-product", null, null, false, "-distance",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> throw new IllegalArgumentException("unsupported PostgreSQL metric: " + metric);
        };
    }

    private static void requireProvider(VectorConnectionDefinition definition) {
        if (definition.provider() != VectorProvider.POSTGRESQL) {
            throw new IllegalArgumentException(
                    "PostgreSQL factory cannot handle provider " + definition.provider());
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("invalid PostgreSQL " + field);
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
        String text = String.valueOf(value);
        if (text.isBlank()) throw new IllegalArgumentException("PostgreSQL setting must not be blank: " + key);
        return text;
    }

    private static String nullableText(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static int integer(Map<String, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PostgreSQL setting must be an integer: " + key);
        }
    }

    private static long positiveLong(Map<String, ?> values, String key, long fallback) {
        Object value = values.get(key);
        long result;
        if (value == null) {
            result = fallback;
        } else if (value instanceof Number number) {
            result = number.longValue();
        } else {
            try {
                result = Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("PostgreSQL setting must be a long: " + key);
            }
        }
        if (result <= 0) {
            throw new IllegalArgumentException("PostgreSQL setting must be greater than zero: " + key);
        }
        return result;
    }

    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("PostgreSQL setting must be a boolean: " + key);
    }

    static final class PostgresqlConnectionHandle implements VectorConnectionHandle {

        private final VectorConnectionId id;
        private final HikariDataSource dataSource;
        private final JdbcTemplate jdbcTemplate;

        private PostgresqlConnectionHandle(
                VectorConnectionId id,
                HikariDataSource dataSource,
                JdbcTemplate jdbcTemplate) {
            this.id = id;
            this.dataSource = dataSource;
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public VectorConnectionId id() {
            return id;
        }

        @Override
        public VectorProvider provider() {
            return VectorProvider.POSTGRESQL;
        }

        JdbcTemplate jdbcTemplate() {
            return jdbcTemplate;
        }

        HikariDataSource dataSource() {
            return dataSource;
        }

        @Override
        public void close() {
            dataSource.close();
        }

        @Override
        public String toString() {
            return "PostgresqlConnectionHandle[id=" + id + ", provider=POSTGRESQL, config=<redacted>]";
        }
    }
}
