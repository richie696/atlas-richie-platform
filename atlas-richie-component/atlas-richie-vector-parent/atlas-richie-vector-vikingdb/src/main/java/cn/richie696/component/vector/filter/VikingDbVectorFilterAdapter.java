/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Objects;

/** Converts the platform's structured filter tree to Spring AI's typed filter tree. */
public final class VikingDbVectorFilterAdapter {

    private VikingDbVectorFilterAdapter() {
    }

    public static Filter.Expression toSpring(VectorFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return switch (filter) {
            case VectorFilter.Eq eq -> expression(Filter.ExpressionType.EQ, eq.field(), eq.value());
            case VectorFilter.In in -> expression(Filter.ExpressionType.IN, in.field(), in.values());
            // VikingDB's scalar filter receives the same membership shape for a
            // collection-valued ACL field; the provider adapter validates the
            // declared field type/index before issuing the request.
            case VectorFilter.ContainsAny containsAny ->
                    expression(Filter.ExpressionType.IN, containsAny.field(), containsAny.values());
            case VectorFilter.Range range -> range(range);
            case VectorFilter.Exists exists -> throw new UnsupportedOperationException(
                    "VikingDB advanced filter does not support EXISTS; use an explicit ACL predicate");
            case VectorFilter.Not not -> new Filter.Expression(
                    Filter.ExpressionType.NOT, toSpring(not.filter()));
            case VectorFilter.And and -> combine(Filter.ExpressionType.AND, and.filters());
            case VectorFilter.Or or -> combine(Filter.ExpressionType.OR, or.filters());
        };
    }

    private static Filter.Expression range(VectorFilter.Range range) {
        if (range.greaterThanOrEqual() == null) {
            return expression(Filter.ExpressionType.LTE, range.field(), range.lessThanOrEqual());
        }
        if (range.lessThanOrEqual() == null) {
            return expression(Filter.ExpressionType.GTE, range.field(), range.greaterThanOrEqual());
        }
        return new Filter.Expression(
                Filter.ExpressionType.AND,
                expression(Filter.ExpressionType.GTE, range.field(), range.greaterThanOrEqual()),
                expression(Filter.ExpressionType.LTE, range.field(), range.lessThanOrEqual()));
    }

    private static Filter.Expression combine(Filter.ExpressionType type, List<VectorFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException("filter group must not be empty");
        }
        Filter.Expression result = toSpring(filters.getFirst());
        for (int i = 1; i < filters.size(); i++) {
            result = new Filter.Expression(type, result, toSpring(filters.get(i)));
        }
        return result;
    }

    private static Filter.Expression expression(Filter.ExpressionType type, String field, Object value) {
        return new Filter.Expression(type, new Filter.Key(field), new Filter.Value(value));
    }
}
