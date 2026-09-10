/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.service.PrecomputedVectorOperations;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.service.impl.PostgresqlPrecomputedVectorOperations;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresqlVectorProviderFactoryTest {

    private final PostgresqlVectorProviderFactory factory = new PostgresqlVectorProviderFactory(null);

    @Test
    void validatesConnectionSettingsWithoutOpeningDatabaseConnection() {
        factory.validateConnection(connection("primary", Map.of()));
        factory.validateConnection(connection("primary", Map.of(
                "jdbc-url", "jdbc:postgresql://pg.internal/vector",
                "maximum-pool-size", "20",
                "minimum-idle", 2,
                "auto-commit", "false")));

        assertThatThrownBy(() -> factory.validateConnection(connection("primary", Map.of("jdbc-urll", "secret"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown PostgreSQL connection settings: [jdbc-urll]");
        assertThatThrownBy(() -> factory.validateConnection(connection(
                "primary", Map.of("jdbc-url", "jdbc:mysql://localhost/vector"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:postgresql");
        assertThatThrownBy(() -> factory.validateConnection(connection(
                "primary", Map.of("maximum-pool-size", 4, "minimum-idle", 5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum-idle <= maximum-pool-size");
    }

    @Test
    void validatesOneControlledStoreIndexAndRejectsUnappliedSettings() {
        factory.validateStore(connection("primary", Map.of()), store("prompt", "prompt_vectors", 2, Map.of()));

        VectorStoreDefinition unknownField = store(
                "prompt", "prompt_vectors", 2, Map.of("schema-name", "prompt", "typo", true));
        assertThatThrownBy(() -> factory.validateStore(connection("primary", Map.of()), unknownField))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown PostgreSQL index additional-fields: [typo]");

        VectorIndexDefinition index = new VectorIndexDefinition(
                "prompt", "prompt_vectors", 2, "cosine", "hnsw", 1, 1, Map.of(), Map.of("m", 16));
        VectorStoreDefinition unappliedIndexParams = new VectorStoreDefinition(
                VectorStoreId.of("prompt-store"), VectorConnectionId.of("primary"), "promptModel",
                "prompt", true, Set.of(), Map.of("prompt", index));
        assertThatThrownBy(() -> factory.validateStore(connection("primary", Map.of()), unappliedIndexParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be empty");
    }

    @Test
    void exposesImplementedCapabilitiesButNotAclSafeHybrid() {
        var capabilities = factory.capabilities(
                connection("primary", Map.of()), store("prompt", "prompt_vectors", 2, Map.of()));

        assertThat(capabilities.ids()).containsExactlyInAnyOrder(
                "NATIVE_FILTER", "ACL_FILTER", "CANDIDATE_VECTOR", "QUERY_TUNING",
                "SCORE_STAGES", "INDEX_LIFECYCLE", "PRECOMPUTED_VECTOR");
        assertThat(capabilities.descriptor(VectorCapability.CANDIDATE_VECTOR).orElseThrow().constraints())
                .containsEntry("client-mmr", "true")
                .containsEntry("server-mmr", "false");
        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isFalse();
        assertThat(capabilities.supports(VectorCapability.SCORE_STAGES)).isTrue();
    }

    @Test
    void declaresAclSafeHybridOnlyForAnExplicitHybridStore() {
        var capabilities = factory.capabilities(connection("primary", Map.of()), store(
                "prompt", "prompt_vectors", 2, Map.of("hybrid-enabled", true, "hybrid-candidate-limit", 10)));

        assertThat(capabilities.supports(VectorCapability.ACL_SAFE_HYBRID)).isTrue();
        assertThat(capabilities.descriptor(VectorCapability.ACL_SAFE_HYBRID).orElseThrow().constraints())
                .containsEntry("filter-stage", "provider-recall").containsEntry("execution", "core-rrf");
    }

    @Test
    void createsIndependentLazyDataSourcesAndClosesThemDeterministically() {
        var first = (PostgresqlVectorProviderFactory.PostgresqlConnectionHandle)
                factory.openConnection(connection("primary", Map.of()));
        var second = (PostgresqlVectorProviderFactory.PostgresqlConnectionHandle)
                factory.openConnection(connection("secondary", Map.of()));

        assertThat(first.dataSource()).isNotSameAs(second.dataSource());
        assertThat(first.jdbcTemplate().getDataSource()).isSameAs(first.dataSource());
        assertThat(first.toString()).doesNotContain("jdbc:").contains("config=<redacted>");

        first.close();
        second.close();
        assertThat(first.dataSource().isClosed()).isTrue();
        assertThat(second.dataSource().isClosed()).isTrue();
    }

    @Test
    void createsStoreBoundHandleAndRejectsUndeclaredPhysicalRouting() {
        VectorConnectionHandle connection = factory.openConnection(connection("primary", Map.of()));
        try {
            VectorStoreDefinition store = store(
                    "prompt", "prompt_vectors", 2, Map.of("schema-name", "prompt_schema"));
            VectorEmbeddingModelBinding model = VectorEmbeddingModelBinding.of("promptModel", embeddingModel(2));

            VectorStoreHandle handle = factory.createStore(connection, store, model);

            assertThat(handle.embeddingModelFingerprint()).isEqualTo(model.fingerprint());
            assertThat(handle.requireCapability(VectorScoreSemantics.class).descriptors()).hasSize(1);
            assertThat(handle.requireCapability(PrecomputedVectorOperations.class)).isNotNull();
            assertThat(handle.requireCapability(VectorAdvancedSearchOperations.class)).isNotNull();
            assertThatThrownBy(() -> handle.service().searchByText(
                    "other_table", "query", 3, SearchOptions.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this PostgreSQL Store");
            assertThatThrownBy(() -> handle.requireCapability(PrecomputedVectorOperations.class)
                    .ensureIndex("other_table", 2, "cosine"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this PostgreSQL Store");
        } finally {
            connection.close();
        }
    }

    @Test
    void oneConnectionCanBackTwoStoreHandlesWithIndependentTables() {
        VectorConnectionHandle connection = factory.openConnection(connection("primary", Map.of()));
        try {
            VectorEmbeddingModelBinding model = VectorEmbeddingModelBinding.of("sharedModel", embeddingModel(2));
            VectorStoreHandle prompts = factory.createStore(connection,
                    store("prompt-store", "prompt", "prompt_vectors", 2, Map.of()), model);
            VectorStoreHandle tools = factory.createStore(connection,
                    store("tool-store", "tools", "tool_vectors", 2, Map.of()), model);

            assertThat(prompts).isNotSameAs(tools);
            assertThat(prompts.definition().connectionId()).isEqualTo(tools.definition().connectionId());
            assertThat(prompts.definition().defaultIndex()).isEqualTo("prompt");
            assertThat(tools.definition().defaultIndex()).isEqualTo("tools");
            assertThatThrownBy(() -> prompts.requireCapability(PrecomputedVectorOperations.class)
                    .ensureIndex("tools", 2, "cosine"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared by this PostgreSQL Store");
        } finally {
            connection.close();
        }
    }

    @Test
    void storeBoundPrecomputedOperationsUseConfiguredSchemaAndPhysicalTable() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PostgresqlPrecomputedVectorOperations operations = new PostgresqlPrecomputedVectorOperations(
                jdbcTemplate, "prompt_schema", Map.of("prompt", "vector_prompt_vectors"));

        operations.ensureIndex("prompt", 2, "cosine");

        assertThat(jdbcTemplate.statements).containsExactly(
                "CREATE TABLE IF NOT EXISTS prompt_schema.vector_prompt_vectors "
                        + "(id TEXT PRIMARY KEY, content TEXT, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, vector vector(2))",
                "CREATE INDEX IF NOT EXISTS vector_prompt_vectors_vector_idx "
                        + "ON prompt_schema.vector_prompt_vectors USING hnsw (vector vector_cosine_ops)");
    }

    @Test
    void registersFactoryWithoutCreatingLegacyDatasourceOrStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PostgresqlVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(PostgresqlVectorProviderFactory.class);
                    assertThat(context).doesNotHaveBean(JdbcTemplate.class);
                    assertThat(context).doesNotHaveBean(HikariDataSource.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void namedSpringTopologyCreatesRegistryHandleAndSingleStoreCompatibilityService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PostgresqlVectorAutoConfiguration.class,
                        VectorAutoConfiguration.class))
                .withBean("aiEmbeddingModel", EmbeddingModel.class, () -> embeddingModel(2))
                .withPropertyValues(
                        "platform.component.vector.connections.pg.provider=postgresql",
                        "platform.component.vector.connections.pg.settings.jdbc-url=jdbc:postgresql://localhost/vector",
                        "platform.component.vector.stores.prompt-store.connection-ref=pg",
                        "platform.component.vector.stores.prompt-store.embedding-model-ref=aiEmbeddingModel",
                        "platform.component.vector.stores.prompt-store.default-index=prompt",
                        "platform.component.vector.stores.prompt-store.indexes.prompt.name=prompt_vectors",
                        "platform.component.vector.stores.prompt-store.indexes.prompt.dimension=2",
                        "platform.component.vector.stores.prompt-store.indexes.prompt.metric=cosine",
                        "platform.component.vector.stores.prompt-store.indexes.prompt.index-type=hnsw",
                        "platform.component.vector.stores.prompt-store.indexes.prompt.additional-fields.schema-name=prompt_schema")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VectorServiceRegistry.class);
                    VectorStoreHandle handle = context.getBean(VectorServiceRegistry.class)
                            .require(VectorStoreId.of("prompt-store"));
                    assertThat(handle.requireCapability(PrecomputedVectorOperations.class)).isNotNull();
                    assertThat(context.getBean(cn.richie696.component.vector.service.VectorService.class))
                            .isSameAs(handle.service());
                });
    }

    private static VectorConnectionDefinition connection(String id, Map<String, Object> settings) {
        return new VectorConnectionDefinition(VectorConnectionId.of(id), VectorProvider.POSTGRESQL, settings);
    }

    private static VectorStoreDefinition store(
            String logicalIndex,
            String physicalIndex,
            int dimension,
            Map<String, Object> additionalFields) {
        return store("prompt-store", logicalIndex, physicalIndex, dimension, additionalFields);
    }

    private static VectorStoreDefinition store(
            String storeId,
            String logicalIndex,
            String physicalIndex,
            int dimension,
            Map<String, Object> additionalFields) {
        VectorIndexDefinition index = new VectorIndexDefinition(
                logicalIndex, physicalIndex, dimension, "cosine", "hnsw", 1, 1,
                additionalFields, Map.of());
        return new VectorStoreDefinition(
                VectorStoreId.of(storeId),
                VectorConnectionId.of("primary"),
                "promptModel",
                logicalIndex,
                true,
                Set.of(),
                Map.of(logicalIndex, index));
    }

    private static EmbeddingModel embeddingModel(int dimensions) {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return dimensions;
                    if ("toString".equals(method.getName())) return "FakeEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final List<String> statements = new ArrayList<>();

        @Override
        public void execute(String sql) {
            statements.add(sql);
        }
    }
}
