package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.model.PrecomputedVectorRecord;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.PrecomputedVectorOperations;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** PostgreSQL/pgvector 的预计算向量数据面，不依赖进程级 EmbeddingModel。 */
public final class PostgresqlPrecomputedVectorOperations implements PrecomputedVectorOperations {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    public PostgresqlPrecomputedVectorOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void ensureIndex(String indexName, int dimension, String metric) {
        validateIndexName(indexName);
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        String table = "vector_%s".formatted(indexName);
        String ops = switch (normalizeMetric(metric)) {
            case "l2" -> "vector_l2_ops";
            case "ip" -> "vector_ip_ops";
            default -> "vector_cosine_ops";
        };
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table
                + " (id TEXT PRIMARY KEY, content TEXT, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, vector vector("
                + dimension + "))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + table + "_vector_idx ON " + table
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
        String sql = "INSERT INTO vector_" + indexName + " (id, content, metadata, vector) "
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
        jdbcTemplate.update("DELETE FROM vector_" + indexName + " WHERE id IN (" + placeholders + ")",
                vectorIds.toArray());
    }

    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit, SearchOptions options) {
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
        String sql = "SELECT id, content, metadata, vector " + operator + " ?::vector AS distance FROM vector_"
                + indexName + filter.sql() + " ORDER BY vector " + operator + " ?::vector LIMIT ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(literal);
        parameters.addAll(filter.parameters());
        parameters.add(literal);
        parameters.add(topK);
        return jdbcTemplate.queryForList(sql, parameters.toArray()).stream()
                .map(row -> toResult(row, operator))
                .filter(result -> result.getScore() >= minScore)
                .toList();
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
                null).setMetadata(toMetadata(row.get("metadata")));
    }

    private String readOperator(String indexName) {
        String definition = jdbcTemplate.query(
                "SELECT indexdef FROM pg_indexes WHERE tablename = ? AND indexdef LIKE '%USING hnsw%' LIMIT 1",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                "vector_" + indexName);
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
    }

    private void validateMetadataField(String field) {
        if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("unsafe metadata field");
        }
    }

    private record SqlFilter(String sql, List<Object> parameters) { }
}
