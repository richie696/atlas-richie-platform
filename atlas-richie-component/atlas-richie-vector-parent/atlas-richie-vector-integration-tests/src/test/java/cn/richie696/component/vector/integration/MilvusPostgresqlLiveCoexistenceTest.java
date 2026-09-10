/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.integration;

import cn.richie696.component.vector.config.MilvusVectorAutoConfiguration;
import cn.richie696.component.vector.config.PostgresqlVectorAutoConfiguration;
import cn.richie696.component.vector.config.VectorAutoConfiguration;
import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.observation.VectorStoreObservationEvent;
import cn.richie696.component.vector.observation.VectorStoreObservationHook;
import cn.richie696.component.vector.query.VectorDiversificationOptions;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.milvus.MilvusQueryOptions;
import cn.richie696.component.vector.query.postgresql.PostgresqlQueryOptions;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.impl.PostgresqlPrecomputedVectorOperations;
import cn.richie696.component.vector.model.PrecomputedVectorRecord;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Live opt-in test; every physical resource is synthetic, uniquely named and exactly removed. */
class MilvusPostgresqlLiveCoexistenceTest {

    @Test
    void startsMilvusAndPostgresqlStoresInOneContextAndKeepsDataIsolated() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(env("VECTOR_IT_RUN", "false")),
                "set VECTOR_IT_RUN=true to execute live provider coexistence");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
        String milvusIndex = "atlas_vector_contract_m_" + suffix;
        String pgSchema = "atlas_vector_contract_s_" + suffix;
        String pgIndex = "pg" + suffix;
        String pgTable = "atlas_vector_contract_t_" + suffix;
        String jdbcUrl = requiredEnv("VECTOR_IT_PG_JDBC_URL");
        String pgUsername = requiredEnv("VECTOR_IT_PG_USERNAME");
        String pgPassword = env("VECTOR_IT_PG_PASSWORD", "");
        List<VectorStoreObservationEvent> events = new CopyOnWriteArrayList<>();

        executePg(jdbcUrl, pgUsername, pgPassword, "CREATE SCHEMA " + pgSchema);
        try {
            List<String> properties = properties(
                    milvusIndex, pgSchema, pgIndex, pgTable, jdbcUrl, pgUsername, pgPassword);
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            VectorAutoConfiguration.class,
                            MilvusVectorAutoConfiguration.class,
                            PostgresqlVectorAutoConfiguration.class))
                    .withBean("syntheticEmbeddingModel", EmbeddingModel.class,
                            MilvusPostgresqlLiveCoexistenceTest::embeddingModel)
                    .withBean("liveVectorObservationHook", VectorStoreObservationHook.class,
                            () -> events::add)
                    .withPropertyValues(properties.toArray(String[]::new))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        VectorServiceRegistry registry = context.getBean(VectorServiceRegistry.class);
                        assertThat(registry.describeStores()).hasSize(2);
                        VectorStoreHandle milvus = registry.require(VectorStoreId.of("milvus-live"));
                        VectorStoreHandle postgresql = registry.require(VectorStoreId.of("postgresql-live"));
                        assertThat(milvus.provider()).isEqualTo(VectorProvider.MILVUS);
                        assertThat(postgresql.provider()).isEqualTo(VectorProvider.POSTGRESQL);

                        VectorIndexLifecycleOperations milvusLifecycle =
                                milvus.requireCapability(VectorIndexLifecycleOperations.class);
                        VectorIndexLifecycleOperations pgLifecycle =
                                postgresql.requireCapability(VectorIndexLifecycleOperations.class);
                        try {
                            milvusLifecycle.createIndex(milvusIndex, indexConfig(milvusIndex));
                            pgLifecycle.createIndex(pgIndex, indexConfig(pgIndex));

                            milvus.service().upsert(VectorRecord.text(
                                    milvusIndex, "milvus-record", "synthetic milvus content"));
                            postgresql.service().upsert(VectorRecord.text(
                                    pgIndex, "postgresql-record", "synthetic postgresql content"));

                            SearchOptions defaults = SearchOptions.builder().rerank(false).build();
                            assertThat(milvus.service().searchByText(
                                    milvusIndex, "synthetic query", 5, defaults))
                                    .extracting(result -> result.getId())
                                    .contains("milvus-record")
                                    .doesNotContain("postgresql-record");
                            assertThat(postgresql.service().searchByText(
                                    pgIndex, "synthetic query", 5, defaults))
                                    .extracting(result -> result.getId())
                                    .contains("postgresql-record")
                                    .doesNotContain("milvus-record");

                            var milvusAdvanced = milvus.requireCapability(VectorAdvancedSearchOperations.class)
                                    .search(new VectorQueryRequest(
                                            "synthetic query", 1, null, null, java.util.Set.of(), 5,
                                            java.time.Duration.ofSeconds(10), null, null,
                                            new MilvusQueryOptions(64, null)));
                            assertThat(milvusAdvanced.results())
                                    .extracting(result -> result.getId())
                                    .containsExactly("milvus-record");
                            assertThat(milvusAdvanced.receipt().parameters()).anySatisfy(parameter -> {
                                assertThat(parameter.name()).isEqualTo("milvus.ef");
                                assertThat(parameter.applied()).isEqualTo("64");
                            });

                            var pgAdvanced = postgresql.requireCapability(VectorAdvancedSearchOperations.class)
                                    .search(new VectorQueryRequest(
                                            "synthetic query", 1, null, null, java.util.Set.of(), 5,
                                            java.time.Duration.ofSeconds(10), null,
                                            new VectorDiversificationOptions(true, false, 0.5D),
                                            new PostgresqlQueryOptions(64, null)));
                            assertThat(pgAdvanced.results())
                                    .singleElement()
                                    .satisfies(result -> {
                                        assertThat(result.getId()).isEqualTo("postgresql-record");
                                        assertThat(result.getVector()).containsExactly(1.0f, 0.0f);
                                    });
                            assertThat(pgAdvanced.receipt().parameters()).anySatisfy(parameter -> {
                                assertThat(parameter.name()).isEqualTo("pgvector.efSearch");
                                assertThat(parameter.applied()).isEqualTo("64");
                                assertThat(parameter.evidence()).isEqualTo("postgresql-set-local");
                            });
                            assertThat(queryPgSetting(
                                    jdbcUrl, pgUsername, pgPassword, "hnsw.ef_search")).isEqualTo("40");
                            assertThat(events).anySatisfy(event -> {
                                assertThat(event.storeId()).isEqualTo(VectorStoreId.of("milvus-live"));
                                assertThat(event.provider()).isEqualTo(VectorProvider.MILVUS);
                            });
                            assertThat(events).anySatisfy(event -> {
                                assertThat(event.storeId()).isEqualTo(VectorStoreId.of("postgresql-live"));
                                assertThat(event.provider()).isEqualTo(VectorProvider.POSTGRESQL);
                            });
                            assertThat(events).allSatisfy(event -> assertThat(event.traceAttributes().toString())
                                    .doesNotContain("synthetic milvus content", "synthetic postgresql content"));
                        } finally {
                            if (milvusLifecycle.indexExists(milvusIndex)) {
                                milvusLifecycle.deleteIndex(milvusIndex);
                            }
                        }
                    });
        } finally {
            executePg(jdbcUrl, pgUsername, pgPassword, "DROP SCHEMA IF EXISTS " + pgSchema + " CASCADE");
        }
    }

    @Test
    void keepsPgvectorTuningTransactionLocalOnTheSameReusedConnection() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(env("VECTOR_IT_RUN", "false")),
                "set VECTOR_IT_RUN=true to execute live provider coexistence");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
        String schema = "atlas_vector_scope_s_" + suffix;
        String logicalIndex = "scope" + suffix;
        String table = "atlas_vector_scope_t_" + suffix;
        String jdbcUrl = requiredEnv("VECTOR_IT_PG_JDBC_URL");
        String username = requiredEnv("VECTOR_IT_PG_USERNAME");
        String password = env("VECTOR_IT_PG_PASSWORD", "");

        executePg(jdbcUrl, username, password, "CREATE SCHEMA " + schema);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            PostgresqlPrecomputedVectorOperations operations = new PostgresqlPrecomputedVectorOperations(
                    jdbc, schema, Map.of(logicalIndex, table));
            operations.ensureIndex(logicalIndex, 2, "cosine");
            operations.upsertPrecomputed(new PrecomputedVectorRecord(
                    "scope-record", logicalIndex, "scope content", Map.of("tenantId", "synthetic"),
                    new float[]{1.0f, 0.0f}));

            assertThat(operations.searchByVector(
                    logicalIndex, new float[]{1.0f, 0.0f}, 1,
                    SearchOptions.builder()
                            .providerSearchParameters(Map.of("pgvector.efSearch", 64))
                            .build()))
                    .extracting(result -> result.getId())
                    .containsExactly("scope-record");
            assertThat(jdbc.queryForObject("SHOW hnsw.ef_search", String.class)).isEqualTo("40");

            operations.searchByVector(
                    logicalIndex, new float[]{1.0f, 0.0f}, 1, SearchOptions.builder().build());
            assertThat(jdbc.queryForObject("SHOW hnsw.ef_search", String.class)).isEqualTo("40");
            dataSource.destroy();
        } finally {
            executePg(jdbcUrl, username, password, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static List<String> properties(
            String milvusIndex,
            String pgSchema,
            String pgIndex,
            String pgTable,
            String jdbcUrl,
            String pgUsername,
            String pgPassword) {
        List<String> values = new ArrayList<>(List.of(
                "platform.component.vector.connections.milvus.provider=milvus",
                "platform.component.vector.connections.milvus.settings.host=" + env("VECTOR_IT_MILVUS_HOST", "127.0.0.1"),
                "platform.component.vector.connections.milvus.settings.port=" + env("VECTOR_IT_MILVUS_PORT", "19530"),
                "platform.component.vector.connections.milvus.settings.database-name=" + env("VECTOR_IT_MILVUS_DATABASE", "default"),
                "platform.component.vector.connections.postgresql.provider=postgresql",
                "platform.component.vector.connections.postgresql.settings.jdbc-url=" + jdbcUrl,
                "platform.component.vector.connections.postgresql.settings.username=" + pgUsername,
                "platform.component.vector.connections.postgresql.settings.password=" + pgPassword,
                "platform.component.vector.connections.postgresql.settings.maximum-pool-size=2",
                "platform.component.vector.connections.postgresql.settings.minimum-idle=0",
                "platform.component.vector.stores.milvus-live.connection-ref=milvus",
                "platform.component.vector.stores.milvus-live.embedding-model-ref=syntheticEmbeddingModel",
                "platform.component.vector.stores.milvus-live.default-index=" + milvusIndex,
                "platform.component.vector.stores.milvus-live.indexes." + milvusIndex + ".name=" + milvusIndex,
                "platform.component.vector.stores.milvus-live.indexes." + milvusIndex + ".dimension=2",
                "platform.component.vector.stores.milvus-live.indexes." + milvusIndex + ".metric=cosine",
                "platform.component.vector.stores.milvus-live.indexes." + milvusIndex + ".index-type=hnsw",
                "platform.component.vector.stores.postgresql-live.connection-ref=postgresql",
                "platform.component.vector.stores.postgresql-live.embedding-model-ref=syntheticEmbeddingModel",
                "platform.component.vector.stores.postgresql-live.default-index=" + pgIndex,
                "platform.component.vector.stores.postgresql-live.indexes." + pgIndex + ".name=" + pgTable,
                "platform.component.vector.stores.postgresql-live.indexes." + pgIndex + ".dimension=2",
                "platform.component.vector.stores.postgresql-live.indexes." + pgIndex + ".metric=cosine",
                "platform.component.vector.stores.postgresql-live.indexes." + pgIndex + ".index-type=hnsw",
                "platform.component.vector.stores.postgresql-live.indexes." + pgIndex
                        + ".additional-fields.schema-name=" + pgSchema));
        String milvusUsername = env("VECTOR_IT_MILVUS_USERNAME", "");
        String milvusPassword = env("VECTOR_IT_MILVUS_PASSWORD", "");
        if (!milvusUsername.isBlank() || !milvusPassword.isBlank()) {
            if (milvusUsername.isBlank() || milvusPassword.isBlank()) {
                throw new IllegalArgumentException("Milvus username and password must be supplied together");
            }
            values.add("platform.component.vector.connections.milvus.settings.username=" + milvusUsername);
            values.add("platform.component.vector.connections.milvus.settings.password=" + milvusPassword);
        }
        return values;
    }

    private static VectorProperties.IndexConfig indexConfig(String name) {
        return new VectorProperties.IndexConfig()
                .setName(name)
                .setDimension(2)
                .setMetric("cosine")
                .setIndexType("hnsw")
                .setShards(1)
                .setReplicas(1)
                .setAdditionalFields(Map.of())
                .setIndexParams(Map.of());
    }

    private static EmbeddingModel embeddingModel() {
        return (EmbeddingModel) Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "dimensions" -> 2;
                    case "embed" -> new float[]{1.0f, 0.0f};
                    case "toString" -> "SyntheticEmbeddingModel";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static void executePg(String jdbcUrl, String username, String password, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String queryPgSetting(
            String jdbcUrl, String username, String password, String setting) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT '[1,0]'::vector");
            try (ResultSet resultSet = statement.executeQuery("SHOW " + setting)) {
            if (!resultSet.next()) throw new IllegalStateException("PostgreSQL setting is unavailable");
            return resultSet.getString(1);
            }
        } catch (Exception error) {
            throw new IllegalStateException("failed to read PostgreSQL setting", error);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured for live provider coexistence");
        }
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
