package cn.richie696.component.vector.config;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.impl.PostgresqlAclAwareHybridSearchOperations;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Service E2E against local pgvector: KNN and tsvector candidate recalls share the ACL SQL predicate. */
@EnabledIfEnvironmentVariable(named = "VECTOR_POSTGRES_IT_RUN", matches = "true")
class PostgresqlAclSafeHybridLiveIT {
    @Test
    void excludesDeniedLexicalWinnerAndKeepsDeleteConsistent() {
        String table = "vector_acl_hybrid_" + UUID.randomUUID().toString().replace("-", "");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(required("VECTOR_POSTGRES_JDBC_URL"));
        config.setUsername(required("VECTOR_POSTGRES_USERNAME"));
        config.setPassword(required("VECTOR_POSTGRES_PASSWORD"));
        try (HikariDataSource source = new HikariDataSource(config)) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbc.execute("CREATE TABLE " + table + " (id uuid primary key, content text, metadata jsonb not null, embedding vector(4), "
                    + "search_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED)");
            try {
                String allowed = UUID.randomUUID().toString();
                String denied = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO " + table + " (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::jsonb, ?::vector)",
                        allowed, "ordinary permitted", "{\"tenantId\":\"tenant-a\"}", "[1,0,0,0]");
                jdbc.update("INSERT INTO " + table + " (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::jsonb, ?::vector)",
                        denied, "rare restricted", "{\"tenantId\":\"tenant-b\"}", "[0,1,0,0]");
                PostgresqlAclAwareHybridSearchOperations hybrid = new PostgresqlAclAwareHybridSearchOperations(
                        jdbc, embedding(), new HybridStoreOptions(true, "embedding", "search_tsv", null, 10,
                        HybridStoreOptions.FallbackMode.CORE_RRF), "public", Map.of("documents", table));

                var results = hybrid.hybridSearch("documents", "ordinary", "rare", 5,
                        HybridSearchOptions.builder().vectorWeight(0.3D).keywordWeight(0.7D).build(),
                        VectorFilter.eq("tenantId", "tenant-a"));
                assertThat(results).extracting(result -> result.getId()).containsExactly(allowed);
                assertThat(results).noneMatch(result -> denied.equals(result.getId()));
                jdbc.update("DELETE FROM " + table + " WHERE id = ?::uuid", allowed);
                assertThat(hybrid.hybridSearch("documents", "ordinary", "ordinary", 5, null,
                        VectorFilter.eq("tenantId", "tenant-a"))).isEmpty();
            } finally {
                jdbc.execute("DROP TABLE IF EXISTS " + table);
            }
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing integration-test environment variable: " + key);
        return value;
    }
    private static EmbeddingModel embedding() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(EmbeddingModel.class.getClassLoader(), new Class<?>[]{EmbeddingModel.class}, (proxy, method, args) -> {
            if ("dimensions".equals(method.getName())) return 4;
            if ("embed".equals(method.getName())) return new float[]{1F, 0F, 0F, 0F};
            return null;
        }));
    }
}
