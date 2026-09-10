package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import io.qdrant.client.grpc.Common;

import java.math.BigInteger;
import java.util.List;

/**
 * Translates the supported common filter subset to Qdrant's gRPC filter model.
 *
 * <p>This mapper is deliberately separate from the Spring AI DSL compiler. Native sparse
 * queries use Qdrant gRPC directly, and ACL safety requires that the exact same protobuf
 * filter be attached to both dense and sparse {@code SearchPoints} requests.</p>
 */
public final class QdrantGrpcFilterMapper {

    public Common.Filter map(VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        return filter(filter);
    }

    private static Common.Filter filter(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> single(field(eq.field(), match(eq.value())));
            case VectorFilter.In in -> single(field(in.field(), matches(in.values())));
            case VectorFilter.ContainsAny values -> single(field(values.field(), matches(values.values())));
            case VectorFilter.Range range -> single(Common.Condition.newBuilder().setField(
                    Common.FieldCondition.newBuilder().setKey(range.field()).setRange(
                            range(range.greaterThanOrEqual(), range.lessThanOrEqual()))).build());
            case VectorFilter.Exists ignored -> throw new UnsupportedOperationException(
                    "Qdrant native hybrid filter does not support exists predicates");
            case VectorFilter.Not not -> Common.Filter.newBuilder().addMustNot(condition(not.filter())).build();
            case VectorFilter.And and -> {
                Common.Filter.Builder builder = Common.Filter.newBuilder();
                and.filters().forEach(item -> builder.addMust(condition(item)));
                yield builder.build();
            }
            case VectorFilter.Or or -> {
                Common.Filter.Builder builder = Common.Filter.newBuilder();
                or.filters().forEach(item -> builder.addShould(condition(item)));
                yield builder.build();
            }
        };
    }

    private static Common.Condition condition(VectorFilter filter) {
        Common.Filter mapped = filter(filter);
        if (mapped.getMustCount() == 1 && mapped.getShouldCount() == 0 && mapped.getMustNotCount() == 0) {
            return mapped.getMust(0);
        }
        return Common.Condition.newBuilder().setFilter(mapped).build();
    }

    private static Common.Filter single(Common.Condition condition) {
        return Common.Filter.newBuilder().addMust(condition).build();
    }

    private static Common.Condition field(String key, Common.Match match) {
        return Common.Condition.newBuilder().setField(Common.FieldCondition.newBuilder()
                .setKey(key).setMatch(match)).build();
    }

    private static Common.Match match(Object value) {
        if (value instanceof String text) return Common.Match.newBuilder().setKeyword(text).build();
        if (isLong(value)) return Common.Match.newBuilder().setInteger(longValue(value)).build();
        throw new UnsupportedOperationException("Qdrant equality supports string or 64-bit integer values only");
    }

    private static Common.Match matches(List<?> values) {
        boolean strings = values.stream().allMatch(String.class::isInstance);
        boolean longs = values.stream().allMatch(QdrantGrpcFilterMapper::isLong);
        if (strings) return Common.Match.newBuilder().setKeywords(Common.RepeatedStrings.newBuilder()
                .addAllStrings(values.stream().map(String.class::cast).toList())).build();
        if (longs) return Common.Match.newBuilder().setIntegers(Common.RepeatedIntegers.newBuilder()
                .addAllIntegers(values.stream().map(QdrantGrpcFilterMapper::longValue).toList())).build();
        throw new UnsupportedOperationException("Qdrant IN/contains-any supports homogeneous string or 64-bit integer values only");
    }

    private static Common.Range range(Object lower, Object upper) {
        Common.Range.Builder builder = Common.Range.newBuilder();
        if (lower != null) builder.setGte(number(lower));
        if (upper != null) builder.setLte(number(upper));
        return builder.build();
    }

    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new UnsupportedOperationException("Qdrant range filters require finite numeric bounds");
        }
        return number.doubleValue();
    }

    private static boolean isLong(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return true;
        return value instanceof BigInteger integer && integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }

    private static long longValue(Object value) {
        return value instanceof BigInteger integer ? integer.longValueExact() : ((Number) value).longValue();
    }
}
