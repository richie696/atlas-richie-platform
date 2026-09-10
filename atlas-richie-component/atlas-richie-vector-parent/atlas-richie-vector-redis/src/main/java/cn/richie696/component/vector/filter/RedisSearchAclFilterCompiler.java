package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

import java.util.List;
import java.util.Locale;

/** RediSearch ACL filter compiler used identically by KNN and full-text candidate queries. */
public final class RedisSearchAclFilterCompiler {
    public String compile(VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        return expression(filter);
    }

    private static String expression(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> tag(eq.field(), List.of(eq.value()));
            case VectorFilter.In in -> tag(in.field(), in.values());
            case VectorFilter.ContainsAny any -> tag(any.field(), any.values());
            case VectorFilter.Range range -> range(range.field(), range.greaterThanOrEqual(), range.lessThanOrEqual());
            case VectorFilter.Exists ignored -> throw new UnsupportedOperationException("Redis hybrid ACL does not support exists predicates");
            case VectorFilter.Not not -> "-(" + expression(not.filter()) + ")";
            case VectorFilter.And and -> and.filters().stream().map(RedisSearchAclFilterCompiler::expression)
                    .map(value -> "(" + value + ")").collect(java.util.stream.Collectors.joining(" "));
            case VectorFilter.Or or -> or.filters().stream().map(RedisSearchAclFilterCompiler::expression)
                    .map(value -> "(" + value + ")").collect(java.util.stream.Collectors.joining("|"));
        };
    }

    private static String tag(String field, List<?> values) {
        if (values.isEmpty() || values.stream().anyMatch(value -> !(value instanceof String || value instanceof Number))) {
            throw new UnsupportedOperationException("Redis hybrid ACL TAG values must be non-empty strings or numbers");
        }
        return "@" + field(field) + ":{" + values.stream().map(value -> escape(String.valueOf(value)))
                .collect(java.util.stream.Collectors.joining("|")) + "}";
    }

    private static String range(String field, Object lower, Object upper) {
        if (lower != null && (!(lower instanceof Number number) || !Double.isFinite(number.doubleValue())))
            throw new UnsupportedOperationException("Redis hybrid ACL range bounds must be finite numbers");
        if (upper != null && (!(upper instanceof Number number) || !Double.isFinite(number.doubleValue())))
            throw new UnsupportedOperationException("Redis hybrid ACL range bounds must be finite numbers");
        return "@" + field(field) + ":[" + (lower == null ? "-inf" : number(lower)) + " "
                + (upper == null ? "+inf" : number(upper)) + "]";
    }

    private static String field(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("invalid Redis ACL field");
        return value;
    }

    private static String number(Object value) { return String.format(Locale.ROOT, "%s", ((Number) value).doubleValue()); }
    private static String escape(String value) { return value.replaceAll("([,\\.<>\\{\\}\\[\\]\\\"':;!@#$%^&*()\\-+=~|/\\\\ ])", "\\\\$1"); }
}
