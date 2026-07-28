package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.VectorFilter;

import java.util.stream.Collectors;

/** 将统一 {@link VectorFilter} 编译为 Milvus V1 expression。 */
public final class MilvusVectorFilterCompiler implements VectorFilterCompiler {

    @Override
    public String compile(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> field(eq.field()) + " == " + literal(eq.value());
            case VectorFilter.In in -> field(in.field()) + " in " + list(in.values());
            case VectorFilter.Range range -> range(range);
            case VectorFilter.Not not -> "!(" + compile(not.filter()) + ")";
            case VectorFilter.And and -> and.filters().stream()
                    .map(this::compile).collect(Collectors.joining(" && ", "(", ")"));
            case VectorFilter.Or or -> or.filters().stream()
                    .map(this::compile).collect(Collectors.joining(" || ", "(", ")"));
            // Milvus scalar列一条记录只承载一个部门/主体值；集合交集退化为该标量值属于主体集合。
            case VectorFilter.ContainsAny containsAny -> field(containsAny.field()) + " in " + list(containsAny.values());
            case VectorFilter.Exists ignored -> throw new UnsupportedOperationException(
                    "Milvus scalar filtering does not support exists; use an explicit scalar equality condition");
        };
    }

    private String range(VectorFilter.Range range) {
        String field = field(range.field());
        if (range.greaterThanOrEqual() == null) {
            return field + " <= " + literal(range.lessThanOrEqual());
        }
        if (range.lessThanOrEqual() == null) {
            return field + " >= " + literal(range.greaterThanOrEqual());
        }
        return "(" + field + " >= " + literal(range.greaterThanOrEqual()) + " && "
                + field + " <= " + literal(range.lessThanOrEqual()) + ")";
    }

    private String list(java.util.List<?> values) {
        return values.stream().map(this::literal).collect(Collectors.joining(", ", "[", "]"));
    }

    private String literal(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String field(String value) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("illegal Milvus scalar field: " + value);
        }
        return value;
    }
}
