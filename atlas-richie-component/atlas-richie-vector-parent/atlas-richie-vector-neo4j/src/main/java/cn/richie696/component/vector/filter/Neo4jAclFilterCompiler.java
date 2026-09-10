package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Compiles ACL predicates to Cypher parameters; neither ACL values nor fields are interpolated unchecked. */
public final class Neo4jAclFilterCompiler {
    public Compiled compile(VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        Map<String, Object> parameters = new LinkedHashMap<>();
        return new Compiled(node(filter, parameters), Map.copyOf(parameters));
    }
    private static String node(VectorFilter filter, Map<String, Object> parameters) {
        return switch (filter) {
            case VectorFilter.Eq eq -> property(eq.field()) + " = $" + add(parameters, eq.value());
            case VectorFilter.In in -> property(in.field()) + " IN $" + add(parameters, in.values());
            case VectorFilter.ContainsAny any -> "any(value IN " + property(any.field()) + " WHERE value IN $" + add(parameters, any.values()) + ")";
            case VectorFilter.Range range -> range(range, parameters);
            case VectorFilter.Exists exists -> property(exists.field()) + " IS NOT NULL";
            case VectorFilter.Not not -> "NOT (" + node(not.filter(), parameters) + ")";
            case VectorFilter.And and -> "(" + and.filters().stream().map(child -> node(child, parameters)).collect(Collectors.joining(" AND ")) + ")";
            case VectorFilter.Or or -> "(" + or.filters().stream().map(child -> node(child, parameters)).collect(Collectors.joining(" OR ")) + ")";
        };
    }
    private static String range(VectorFilter.Range range, Map<String, Object> parameters) {
        if (range.greaterThanOrEqual() != null && !(range.greaterThanOrEqual() instanceof Number)) throw new UnsupportedOperationException("Neo4j hybrid ACL range bounds must be numeric");
        if (range.lessThanOrEqual() != null && !(range.lessThanOrEqual() instanceof Number)) throw new UnsupportedOperationException("Neo4j hybrid ACL range bounds must be numeric");
        String prop = property(range.field());
        String lower = range.greaterThanOrEqual() == null ? null : prop + " >= $" + add(parameters, range.greaterThanOrEqual());
        String upper = range.lessThanOrEqual() == null ? null : prop + " <= $" + add(parameters, range.lessThanOrEqual());
        return lower == null ? "(" + upper + ")" : upper == null ? "(" + lower + ")" : "(" + lower + " AND " + upper + ")";
    }
    private static String add(Map<String, Object> parameters, Object value) { String key = "acl" + parameters.size(); parameters.put(key, value); return key; }
    private static String property(String field) {
        if (field == null || !field.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("invalid Neo4j metadata field");
        return "n.`metadata." + field + "`";
    }
    public record Compiled(String cypher, Map<String, Object> parameters) { }
}
