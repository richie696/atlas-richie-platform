/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

import java.math.BigInteger;
import java.util.List;

/**
 * Compiles the subset of the common filter tree that Spring AI 2.0 can
 * faithfully convert to a native Qdrant gRPC filter.
 */
public final class QdrantVectorFilterCompiler implements VectorFilterCompiler {

    private final SpringAiVectorFilterCompiler delegate = new SpringAiVectorFilterCompiler();

    @Override
    public String compile(VectorFilter filter) {
        validate(filter);
        return delegate.compile(filter);
    }

    private static void validate(VectorFilter filter) {
        switch (filter) {
            case VectorFilter.Eq eq -> requireComparable(eq.value(), "Qdrant equality");
            case VectorFilter.In in -> requireHomogeneousValues(in.values(), "Qdrant IN");
            case VectorFilter.ContainsAny containsAny ->
                    requireHomogeneousValues(containsAny.values(), "Qdrant contains-any");
            case VectorFilter.Range range -> {
                requireRangeBound(range.greaterThanOrEqual());
                requireRangeBound(range.lessThanOrEqual());
            }
            case VectorFilter.Exists ignored -> throw new UnsupportedOperationException(
                    "Qdrant Spring AI adapter does not support exists filters");
            case VectorFilter.Not not -> validate(not.filter());
            case VectorFilter.And and -> and.filters().forEach(QdrantVectorFilterCompiler::validate);
            case VectorFilter.Or or -> or.filters().forEach(QdrantVectorFilterCompiler::validate);
        }
    }

    private static void requireComparable(Object value, String operation) {
        if (!(value instanceof String) && !isLongCompatible(value)) {
            throw new UnsupportedOperationException(operation + " supports string or 64-bit integer values only");
        }
    }

    private static void requireHomogeneousValues(List<?> values, String operation) {
        boolean strings = values.stream().allMatch(String.class::isInstance);
        boolean integers = values.stream().allMatch(QdrantVectorFilterCompiler::isLongCompatible);
        if (!strings && !integers) {
            throw new UnsupportedOperationException(
                    operation + " supports a homogeneous string or 64-bit integer list only");
        }
    }

    private static void requireRangeBound(Object value) {
        if (value == null) return;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new UnsupportedOperationException("Qdrant range filters require finite numeric bounds");
        }
    }

    private static boolean isLongCompatible(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return true;
        }
        if (value instanceof BigInteger integer) {
            return integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                    && integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
        }
        return false;
    }
}
