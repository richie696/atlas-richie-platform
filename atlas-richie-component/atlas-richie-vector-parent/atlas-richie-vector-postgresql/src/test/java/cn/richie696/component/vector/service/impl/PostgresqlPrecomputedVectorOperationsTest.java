package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.model.SearchOptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresqlPrecomputedVectorOperationsTest {

    @Test
    void searchByVector_appliesPgvectorSettingsOnSameTransactionConnection() {
        RecordingConnection connection = new RecordingConnection();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(connection.proxy());

        new PostgresqlPrecomputedVectorOperations(jdbcTemplate).searchByVector(
                "prompt_strategy_1",
                new float[]{0.1f, 0.2f},
                5,
                SearchOptions.builder()
                        .providerSearchParameters(Map.of(
                                "pgvector.efSearch", 128,
                                "pgvector.ivfflatProbes", 4))
                        .build());

        assertThat(connection.events).containsSubsequence(
                "setAutoCommit:false",
                "prepare:SELECT set_config(?, ?, true)",
                "execute:SELECT set_config(?, ?, true)",
                "prepare:SELECT set_config(?, ?, true)",
                "execute:SELECT set_config(?, ?, true)",
                "prepare:SELECT id, content, metadata, vector <=> ?::vector AS distance FROM vector_prompt_strategy_1 ORDER BY vector <=> ?::vector LIMIT ?",
                "executeQuery:SELECT id, content, metadata, vector <=> ?::vector AS distance FROM vector_prompt_strategy_1 ORDER BY vector <=> ?::vector LIMIT ?",
                "commit",
                "setAutoCommit:true");
        assertThat(connection.stringParameters.values())
                .contains("hnsw.ef_search", "128", "ivfflat.probes", "4");
    }

    @Test
    void searchByVector_rejectsUnknownProviderParameterBeforeQuery() {
        RecordingConnection connection = new RecordingConnection();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(connection.proxy());

        assertThatThrownBy(() -> new PostgresqlPrecomputedVectorOperations(jdbcTemplate).searchByVector(
                "prompt_strategy_1",
                new float[]{0.1f, 0.2f},
                5,
                SearchOptions.builder()
                        .providerSearchParameters(Map.of("pgvector.unknown", 1))
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported PostgreSQL provider search parameter");

        assertThat(connection.events).containsExactly(
                "setAutoCommit:false", "rollback", "setAutoCommit:true");
    }

    @Test
    void searchByVector_withoutAdvancedParametersDoesNotExecuteTuningSql() {
        RecordingConnection connection = new RecordingConnection();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(connection.proxy());

        new PostgresqlPrecomputedVectorOperations(jdbcTemplate).searchByVector(
                "prompt_strategy_1",
                new float[]{0.1f, 0.2f},
                5,
                SearchOptions.builder().build());

        assertThat(connection.events).noneMatch(event -> event.contains("set_config"));
        assertThat(connection.events).containsSubsequence(
                "setAutoCommit:false",
                "prepare:SELECT id, content, metadata, vector <=> ?::vector AS distance FROM vector_prompt_strategy_1 ORDER BY vector <=> ?::vector LIMIT ?",
                "commit",
                "setAutoCommit:true");
    }

    @Test
    void candidateVectorPathAddsProjectionOnlyWhenExplicitlyEnabled() {
        RecordingConnection connection = new RecordingConnection();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(connection.proxy());

        new PostgresqlPrecomputedVectorOperations(jdbcTemplate).searchByVector(
                "prompt_strategy_1",
                new float[]{0.1f, 0.2f},
                5,
                SearchOptions.builder().build(),
                true);

        assertThat(connection.events).anyMatch(event -> event.contains(
                "SELECT id, content, metadata, vector::text AS raw_vector, vector <=>"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final Connection connection;

        private RecordingJdbcTemplate(Connection connection) {
            this.connection = connection;
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> extractor, Object... args) {
            return null;
        }

        @Override
        public <T> T execute(ConnectionCallback<T> action) {
            try {
                return action.doInConnection(connection);
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class RecordingConnection {

        private final List<String> events = new ArrayList<>();
        private final Map<String, String> stringParameters = new LinkedHashMap<>();

        private Connection proxy() {
            return Connection.class.cast(Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getAutoCommit" -> true;
                        case "setAutoCommit" -> {
                            events.add("setAutoCommit:" + arguments[0]);
                            yield null;
                        }
                        case "prepareStatement" -> preparedStatement((String) arguments[0]);
                        case "commit", "rollback" -> {
                            events.add(method.getName());
                            yield null;
                        }
                        case "toString" -> "RecordingConnection";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    }));
        }

        private PreparedStatement preparedStatement(String sql) {
            events.add("prepare:" + sql);
            return PreparedStatement.class.cast(Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "setString" -> {
                            stringParameters.put(sql + "#" + arguments[0] + "#" + stringParameters.size(),
                                    String.valueOf(arguments[1]));
                            yield null;
                        }
                        case "setObject", "close" -> null;
                        case "execute" -> {
                            events.add("execute:" + sql);
                            yield true;
                        }
                        case "executeQuery" -> {
                            events.add("executeQuery:" + sql);
                            yield resultSet();
                        }
                        case "toString" -> "RecordingPreparedStatement";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    }));
        }

        private ResultSet resultSet() {
            return ResultSet.class.cast(Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "next" -> false;
                        case "close" -> null;
                        case "toString" -> "EmptyResultSet";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    }));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
