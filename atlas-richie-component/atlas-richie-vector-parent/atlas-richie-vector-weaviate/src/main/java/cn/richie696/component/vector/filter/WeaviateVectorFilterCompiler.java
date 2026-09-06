package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Compiles the safe, structured filter tree to Weaviate GraphQL where syntax. */
public final class WeaviateVectorFilterCompiler implements VectorFilterCompiler {

    private static final String META_FIELD_PREFIX = "meta_";

    @Override
    public String compile(VectorFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return switch (filter) {
            case VectorFilter.Eq eq -> comparison("Equal", eq.field(), eq.value());
            case VectorFilter.In in -> comparison("In", in.field(), in.values());
            case VectorFilter.ContainsAny containsAny -> comparison("ContainsAny", containsAny.field(), containsAny.values());
            case VectorFilter.Range range -> range(range);
            case VectorFilter.Exists exists -> "{path:[\"" + field(exists.field()) + "\"],operator:IsNotNull}";
            case VectorFilter.Not not -> "{operator:Not,operands:[" + compile(not.filter()) + "]}";
            case VectorFilter.And and -> compound("And", and.filters());
            case VectorFilter.Or or -> compound("Or", or.filters());
        };
    }

    private String range(VectorFilter.Range range) {
        if (range.greaterThanOrEqual() == null) {
            return comparison("LessThanEqual", range.field(), range.lessThanOrEqual());
        }
        if (range.lessThanOrEqual() == null) {
            return comparison("GreaterThanEqual", range.field(), range.greaterThanOrEqual());
        }
        return compound("And", List.of(
                VectorFilter.range(range.field(), range.greaterThanOrEqual(), null),
                VectorFilter.range(range.field(), null, range.lessThanOrEqual())));
    }

    private String compound(String operator, List<VectorFilter> filters) {
        return "{operator:" + operator + ",operands:[" + filters.stream()
                .map(this::compile).collect(Collectors.joining(",")) + "]}";
    }

    private String comparison(String operator, String field, Object value) {
        if (value instanceof List<?> values) {
            if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Weaviate filter values must not be empty or null");
            }
            if (values.stream().allMatch(String.class::isInstance)) {
                return "{path:[\"" + field(field) + "\"],operator:" + operator
                        + ",valueTextArray:" + stringArray(values) + "}";
            }
            throw new UnsupportedOperationException("Weaviate list filters currently support string values only");
        }
        String valueField;
        String literal;
        if (value instanceof Boolean) {
            valueField = "valueBoolean";
            literal = value.toString();
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            valueField = "valueInt";
            literal = value.toString();
        } else if (value instanceof Number) {
            valueField = "valueNumber";
            literal = value.toString();
        } else {
            valueField = "valueText";
            literal = quote(String.valueOf(value));
        }
        return "{path:[\"" + field(field) + "\"],operator:" + operator + "," + valueField + ":" + literal + "}";
    }

    private String stringArray(List<?> values) {
        return values.stream().map(value -> quote(String.valueOf(value))).collect(Collectors.joining(",", "[", "]"));
    }

    private String field(String field) {
        if (field == null || !field.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("illegal Weaviate filter field: " + field);
        }
        return switch (field) {
            case "_id", "_creationTimeUnix", "_lastUpdateTimeUnix" -> field;
            default -> META_FIELD_PREFIX + field;
        };
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
