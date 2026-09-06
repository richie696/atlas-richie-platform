package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.model.PrecomputedVectorRecord;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.PrecomputedVectorOperations;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** PostgreSQL/pgvector 的预计算向量数据面，不依赖进程级 EmbeddingModel。 */
public final class PostgresqlPrecomputedVectorOperations implements PrecomputedVectorOperations {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> PROVIDER_SETTINGS = Map.of(
            "pgvector.efSearch", "hnsw.ef_search",
            "pgvector.ivfflatProbes", "ivfflat.probes");
    private final JdbcTemplate jdbcTemplate;
    private final String schemaName;
    private final Map<String, String> managedTables;

    public PostgresqlPrecomputedVectorOperations(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, Map.of());
    }

    /** Store-bound constructor used by Named Multi-store. */
    public PostgresqlPrecomputedVectorOperations(
            JdbcTemplate jdbcTemplate,
            String schemaName,
            Map<String, String> managedTables) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaName = schemaName;
        this.managedTables = Map.copyOf(managedTables == null ? Map.of() : managedTables);
    }

    @Override
    public void ensureIndex(String indexName, int dimension, String metric) {
        validateIndexName(indexName);
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        String table = tableName(indexName);
        String index = physicalTableName(indexName) + "_vector_idx";
        String ops = switch (normalizeMetric(metric)) {
            case "l2" -> "vector_l2_ops";
            case "ip" -> "vector_ip_ops";
            default -> "vector_cosine_ops";
        };
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table
                + " (id TEXT PRIMARY KEY, content TEXT, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, vector vector("
                + dimension + "))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + index + " ON " + table
                + " USING hnsw (vector " + ops + ")");
    }

    @Override
    public void upsertPrecomputed(PrecomputedVectorRecord record) {
        upsertAllPrecomputed(record.indexName(), List.of(record));
    }

    @Override
    public void upsertAllPrecomputed(String indexName, List<PrecomputedVectorRecord> records) {
        validateIndexName(indexName);
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + tableName(indexName) + " (id, content, metadata, vector) "
                + "VALUES (?, ?, ?::jsonb, ?::vector) ON CONFLICT (id) DO UPDATE SET "
                + "content = EXCLUDED.content, metadata = EXCLUDED.metadata, vector = EXCLUDED.vector";
        List<Object[]> arguments = records.stream().map(record -> {
            if (!indexName.equals(record.indexName())) {
                throw new IllegalArgumentException("record indexName does not match batch indexName");
            }
            return new Object[]{record.id(), record.content(), toJson(record.metadata()), toVector(record.vector())};
        }).toList();
        jdbcTemplate.batchUpdate(sql, arguments);
    }

    @Override
    public void deletePrecomputed(String indexName, List<String> vectorIds) {
        validateIndexName(indexName);
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        String placeholders = vectorIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM " + tableName(indexName) + " WHERE id IN (" + placeholders + ")",
                vectorIds.toArray());
    }

    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit, SearchOptions options) {
        return searchByVector(indexName, vector, limit, options, false);
    }

    List<VectorSearchResult> searchByVector(
            String indexName, float[] vector, int limit, SearchOptions options, boolean includeVectors) {
        validateIndexName(indexName);
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        SearchOptions effective = options == null ? SearchOptions.builder().build() : options;
        int topK = limit > 0 ? limit : 10;
        double minScore = effective.getMinScore() == null ? 0.0 : effective.getMinScore();
        String operator = readOperator(indexName);
        SqlFilter filter = compileFilter(effective.getFilter());
        String literal = toVector(vector);
        String vectorProjection = includeVectors ? ", vector::text AS raw_vector" : "";
        String sql = "SELECT id, content, metadata" + vectorProjection + ", vector " + operator + " ?::vector AS distance FROM "
                + tableName(indexName) + filter.sql() + " ORDER BY vector " + operator + " ?::vector LIMIT ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(literal);
        parameters.addAll(filter.parameters());
        parameters.add(literal);
        parameters.add(topK);
        return jdbcTemplate.execute((ConnectionCallback<List<VectorSearchResult>>) connection -> executeSearch(
                connection, sql, parameters, operator, minScore,
                effective.getProviderSearchParameters(), includeVectors));
    }

    /**
     * Execute provider-local query settings and the vector query on one connection.
     * <p>
     * PostgreSQL's {@code SET LOCAL} is transaction scoped.  A plain JdbcTemplate
     * call would borrow a different connection for the setting and the SELECT, or
     * immediately reset the setting when auto-commit is enabled.  The callback
     * therefore owns a short transaction only when the caller has not already
     * supplied one, and never interpolates user-controlled setting names or values.
     * </p>
     */
    private List<VectorSearchResult> executeSearch(
            Connection connection,
            String sql,
            List<Object> parameters,
            String operator,
            double minScore,
            Map<String, Integer> providerSearchParameters,
            boolean includeVectors) throws SQLException {
        boolean ownsTransaction = connection.getAutoCommit();
        if (ownsTransaction) {
            connection.setAutoCommit(false);
        }
        try {
            applyProviderSearchParameters(connection, providerSearchParameters);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<VectorSearchResult> results = new ArrayList<>();
                    while (resultSet.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", resultSet.getObject("id"));
                        row.put("content", resultSet.getObject("content"));
                        row.put("metadata", resultSet.getObject("metadata"));
                        row.put("distance", resultSet.getObject("distance"));
                        if (includeVectors) {
                            row.put("raw_vector", resultSet.getString("raw_vector"));
                        }
                        VectorSearchResult result = toResult(row, operator);
                        if (result.getScore() >= minScore) {
                            results.add(result);
                        }
                    }
                    if (ownsTransaction) {
                        connection.commit();
                    }
                    return List.copyOf(results);
                }
            }
        } catch (SQLException | RuntimeException error) {
            if (ownsTransaction) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            throw error;
        } finally {
            if (ownsTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    private void applyProviderSearchParameters(Connection connection, Map<String, Integer> parameters)
            throws SQLException {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList()) {
            String setting = PROVIDER_SETTINGS.get(entry.getKey());
            if (setting == null) {
                throw new IllegalArgumentException("unsupported PostgreSQL provider search parameter: " + entry.getKey());
            }
            Integer value = entry.getValue();
            if (value == null || value < 1 || value > 32_768) {
                throw new IllegalArgumentException("PostgreSQL provider search parameter must be between 1 and 32768: "
                        + entry.getKey());
            }
            // set_config(..., true) is the parameterized equivalent of SET LOCAL.
            try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
                statement.setString(1, setting);
                statement.setString(2, value.toString());
                statement.execute();
            }
        }
    }

    private VectorSearchResult toResult(Map<String, Object> row, String operator) {
        double distance = ((Number) row.getOrDefault("distance", 0.0)).doubleValue();
        double score = switch (operator) {
            case "<=>" -> 1.0 - distance;
            case "<#>" -> -distance;
            default -> 1.0 / (1.0 + distance);
        };
        return VectorSearchResult.of(
                String.valueOf(row.get("id")),
                String.valueOf(row.getOrDefault("content", "")),
                score,
                parseVector(row.get("raw_vector"))).setMetadata(toMetadata(row.get("metadata")));
    }

    private float[] parseVector(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.length() < 2 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw new IllegalArgumentException("PostgreSQL returned an invalid candidate vector");
        }
        String body = text.substring(1, text.length() - 1).trim();
        if (body.isEmpty()) return new float[0];
        String[] values = body.split(",");
        float[] vector = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            vector[index] = Float.parseFloat(values[index].trim());
        }
        return vector;
    }

    private String readOperator(String indexName) {
        String sql = schemaName == null
                ? "SELECT indexdef FROM pg_indexes WHERE tablename = ? AND indexdef LIKE '%USING hnsw%' LIMIT 1"
                : "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND tablename = ? "
                        + "AND indexdef LIKE '%USING hnsw%' LIMIT 1";
        String definition = schemaName == null
                ? jdbcTemplate.query(sql, resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                        physicalTableName(indexName))
                : jdbcTemplate.query(sql, resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                        schemaName, physicalTableName(indexName));
        if (definition == null || definition.contains("vector_cosine_ops")) {
            return "<=>";
        }
        if (definition.contains("vector_ip_ops")) {
            return "<#>";
        }
        return "<->";
    }

    private SqlFilter compileFilter(VectorFilter filter) {
        if (filter == null) {
            return new SqlFilter("", List.of());
        }
        List<Object> parameters = new ArrayList<>();
        return new SqlFilter(" WHERE " + compileNode(filter, parameters), List.copyOf(parameters));
    }

    private String compileNode(VectorFilter filter, List<Object> parameters) {
        return switch (filter) {
            case VectorFilter.Eq eq -> {
                validateMetadataField(eq.field());
                parameters.add(eq.value().toString());
                yield "metadata ->> '" + eq.field() + "' = ?";
            }
            case VectorFilter.In in -> {
                validateMetadataField(in.field());
                String values = in.values().stream().map(value -> {
                    parameters.add(value.toString());
                    return "?";
                }).collect(Collectors.joining(","));
                yield "metadata ->> '" + in.field() + "' IN (" + values + ")";
            }
            case VectorFilter.Exists exists -> {
                validateMetadataField(exists.field());
                yield "metadata ? '" + exists.field() + "'";
            }
            case VectorFilter.And and -> and.filters().stream()
                    .map(child -> "(" + compileNode(child, parameters) + ")")
                    .collect(Collectors.joining(" AND "));
            case VectorFilter.Or or -> or.filters().stream()
                    .map(child -> "(" + compileNode(child, parameters) + ")")
                    .collect(Collectors.joining(" OR "));
            case VectorFilter.Not not -> "NOT (" + compileNode(not.filter(), parameters) + ")";
            default -> throw new UnsupportedOperationException(
                    "unsupported PostgreSQL metadata filter: " + filter.getClass().getSimpleName());
        };
    }

    private Map<String, Object> toMetadata(Object value) {
        try {
            if (value instanceof Map<?, ?> map) {
                return map.entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().toString(), Map.Entry::getValue));
            }
            String json = String.valueOf(value);
            return JSON.readValue(json, new TypeReference<>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception error) {
            throw new IllegalArgumentException("metadata cannot be serialized", error);
        }
    }

    private String toVector(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) result.append(',');
            result.append(vector[index]);
        }
        return result.append(']').toString();
    }

    private String normalizeMetric(String metric) {
        if (metric == null) return "cosine";
        return switch (metric.toLowerCase()) {
            case "l2", "euclidean" -> "l2";
            case "ip", "dot" -> "ip";
            default -> "cosine";
        };
    }

    private void validateIndexName(String indexName) {
        if (indexName == null || !indexName.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("unsafe indexName");
        }
        if (!managedTables.isEmpty() && !managedTables.containsKey(indexName)) {
            throw new IllegalArgumentException("index is not declared by this PostgreSQL Store: " + indexName);
        }
    }

    private String tableName(String indexName) {
        String table = physicalTableName(indexName);
        return schemaName == null ? table : schemaName + "." + table;
    }

    private String physicalTableName(String indexName) {
        validateIndexName(indexName);
        return managedTables.isEmpty() ? "vector_" + indexName : managedTables.get(indexName);
    }

    private void validateMetadataField(String field) {
        if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("unsafe metadata field");
        }
    }

    private record SqlFilter(String sql, List<Object> parameters) { }
}
