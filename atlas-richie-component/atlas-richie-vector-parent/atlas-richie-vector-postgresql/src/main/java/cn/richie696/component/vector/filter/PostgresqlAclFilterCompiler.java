package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Compiles structured ACL predicates to parameterized JSONB SQL; values are never interpolated. */
public final class PostgresqlAclFilterCompiler {
    public Compiled compile(VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        List<Object> parameters = new ArrayList<>();
        return new Compiled(node(filter, parameters), List.copyOf(parameters));
    }

    private static String node(VectorFilter filter, List<Object> parameters) {
        return switch (filter) {
            case VectorFilter.Eq eq -> equals(eq.field(), eq.value(), parameters);
            case VectorFilter.In in -> "(" + in.values().stream().map(value -> equals(in.field(), value, parameters))
                    .collect(Collectors.joining(" OR ")) + ")";
            case VectorFilter.ContainsAny any -> "(" + any.values().stream().map(value -> contains(any.field(), value, parameters))
                    .collect(Collectors.joining(" OR ")) + ")";
            case VectorFilter.Range range -> range(range, parameters);
            case VectorFilter.Exists exists -> "metadata ? '" + field(exists.field()) + "'";
            case VectorFilter.Not not -> "NOT (" + node(not.filter(), parameters) + ")";
            case VectorFilter.And and -> "(" + and.filters().stream().map(child -> node(child, parameters))
                    .collect(Collectors.joining(" AND ")) + ")";
            case VectorFilter.Or or -> "(" + or.filters().stream().map(child -> node(child, parameters))
                    .collect(Collectors.joining(" OR ")) + ")";
        };
    }

    private static String equals(String field, Object value, List<Object> parameters) {
        parameters.add(String.valueOf(value));
        return "metadata ->> '" + field(field) + "' = ?";
    }

    private static String contains(String field, Object value, List<Object> parameters) {
        parameters.add("{\"" + field(field) + "\":[\"" + json(String.valueOf(value)) + "\"]}");
        return "metadata @> ?::jsonb";
    }

    private static String range(VectorFilter.Range range, List<Object> parameters) {
        String field = field(range.field());
        List<String> clauses = new ArrayList<>();
        if (range.greaterThanOrEqual() != null) {
            parameters.add(number(range.greaterThanOrEqual()));
            clauses.add("NULLIF(metadata ->> '" + field + "', '')::double precision >= ?");
        }
        if (range.lessThanOrEqual() != null) {
            parameters.add(number(range.lessThanOrEqual()));
            clauses.add("NULLIF(metadata ->> '" + field + "', '')::double precision <= ?");
        }
        return "(" + String.join(" AND ", clauses) + ")";
    }

    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new UnsupportedOperationException("PostgreSQL hybrid ACL range bounds must be finite numbers");
        return number.doubleValue();
    }

    private static String field(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("invalid PostgreSQL metadata field");
        return value;
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    public record Compiled(String sql, List<Object> parameters) { }
}
