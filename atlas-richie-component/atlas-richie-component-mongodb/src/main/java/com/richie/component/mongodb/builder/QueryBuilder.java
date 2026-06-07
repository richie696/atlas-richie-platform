package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantContext;
import com.richie.component.mongodb.observability.MongodbMetricsRecorder;
import com.richie.component.mongodb.observability.MongodbSlowQueryLogger;
import com.richie.component.mongodb.observability.MongodbTracing;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class QueryBuilder<T> {
    private final Class<T> entityClass;
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;
    private final MongodbTracing tracing;
    private final MongodbMetricsRecorder metricsRecorder;
    private final MongodbSlowQueryLogger slowQueryLogger;
    private final Query query = new Query();
    private Criteria criteria;
    private boolean criteriaStarted = false;
    private final List<String> selectFields = new ArrayList<>();
    private PageRequest pageRequest;
    private long skip;
    private int limit;
    private boolean used;
    private boolean bypassTenant = false;
    private boolean ignoreSoftDelete = false;

    public QueryBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
        this(entityClass, mongoTemplate, entityIntrospector, null, null, null);
    }

    public QueryBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector,
                        MongodbTracing tracing, MongodbMetricsRecorder metricsRecorder, MongodbSlowQueryLogger slowQueryLogger) {
        this.entityClass = entityClass;
        this.mongoTemplate = mongoTemplate;
        this.entityIntrospector = entityIntrospector;
        this.tracing = tracing;
        this.metricsRecorder = metricsRecorder;
        this.slowQueryLogger = slowQueryLogger;
    }

    private String toFieldName(LambdaField<T, ?> field) {
        return entityIntrospector.resolveFieldName(field);
    }

    private void startCriteria() {
        if (!criteriaStarted) {
            criteria = new Criteria();
            criteriaStarted = true;
        }
    }

    public QueryBuilder<T> eq(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).is(value);
        return this;
    }

    public QueryBuilder<T> eq(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) eq(field, value);
        return this;
    }

    public QueryBuilder<T> ne(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).ne(value);
        return this;
    }

    public QueryBuilder<T> ne(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) ne(field, value);
        return this;
    }

    public QueryBuilder<T> gt(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).gt(value);
        return this;
    }

    public QueryBuilder<T> gt(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) gt(field, value);
        return this;
    }

    public QueryBuilder<T> ge(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).gte(value);
        return this;
    }

    public QueryBuilder<T> ge(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) ge(field, value);
        return this;
    }

    public QueryBuilder<T> lt(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).lt(value);
        return this;
    }

    public QueryBuilder<T> lt(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) lt(field, value);
        return this;
    }

    public QueryBuilder<T> le(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).lte(value);
        return this;
    }

    public QueryBuilder<T> le(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) le(field, value);
        return this;
    }

    public QueryBuilder<T> between(LambdaField<T, ?> field, Object from, Object to) {
        if (from == null && to == null) return this;
        startCriteria();
        if (from != null && to != null) {
            criteria.and(toFieldName(field)).gte(from).lte(to);
        } else if (from != null) {
            criteria.and(toFieldName(field)).gte(from);
        } else {
            criteria.and(toFieldName(field)).lte(to);
        }
        return this;
    }

    public QueryBuilder<T> between(boolean condition, LambdaField<T, ?> field, Object from, Object to) {
        if (condition) between(field, from, to);
        return this;
    }

    public QueryBuilder<T> like(LambdaField<T, ?> field, String pattern) {
        if (pattern == null || pattern.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).regex(pattern);
        return this;
    }

    public QueryBuilder<T> like(boolean condition, LambdaField<T, ?> field, String pattern) {
        if (condition) like(field, pattern);
        return this;
    }

    public QueryBuilder<T> in(LambdaField<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).in(values);
        return this;
    }

    public QueryBuilder<T> nin(LambdaField<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).nin(values);
        return this;
    }

    public QueryBuilder<T> exists(LambdaField<T, ?> field) {
        startCriteria();
        criteria.and(toFieldName(field)).exists(true);
        return this;
    }

    public QueryBuilder<T> isNull(LambdaField<T, ?> field) {
        startCriteria();
        criteria.and(toFieldName(field)).is(null);
        return this;
    }

    public QueryBuilder<T> isNotNull(LambdaField<T, ?> field) {
        startCriteria();
        criteria.and(toFieldName(field)).ne(null);
        return this;
    }

    public QueryBuilder<T> and(LambdaField<T, ?> field) {
        startCriteria();
        criteria.and(toFieldName(field));
        return this;
    }

    public QueryBuilder<T> or(QueryBuilder<T> other) {
        if (other == null || other.criteria == null) return this;
        startCriteria();
        Criteria orCriteria = new Criteria().orOperator(this.criteria, other.criteria);
        query.addCriteria(orCriteria);
        return this;
    }

    public QueryBuilder<T> orderByAsc(LambdaField<T, ?> field) {
        query.with(Sort.by(Sort.Direction.ASC, toFieldName(field)));
        return this;
    }

    public QueryBuilder<T> orderByDesc(LambdaField<T, ?> field) {
        query.with(Sort.by(Sort.Direction.DESC, toFieldName(field)));
        return this;
    }

    public QueryBuilder<T> orderByAsc(String fieldName) {
        query.with(Sort.by(Sort.Direction.ASC, fieldName));
        return this;
    }

    public QueryBuilder<T> orderByDesc(String fieldName) {
        query.with(Sort.by(Sort.Direction.DESC, fieldName));
        return this;
    }

    public QueryBuilder<T> select(LambdaField<T, ?>... fields) {
        for (LambdaField<T, ?> field : fields) {
            selectFields.add(toFieldName(field));
        }
        return this;
    }

    public QueryBuilder<T> select(String... fieldNames) {
        for (String fieldName : fieldNames) {
            selectFields.add(fieldName);
        }
        return this;
    }

    public QueryBuilder<T> page(int pageNum, int pageSize) {
        this.pageRequest = PageRequest.of(pageNum - 1, pageSize);
        return this;
    }

    public QueryBuilder<T> skip(long n) {
        this.skip = n;
        return this;
    }

    public QueryBuilder<T> limit(int n) {
        this.limit = n;
        return this;
    }

    public QueryBuilder<T> bypassTenant() {
        this.bypassTenant = true;
        return this;
    }

    public QueryBuilder<T> ignoreSoftDelete() {
        this.ignoreSoftDelete = true;
        return this;
    }

    public List<T> list() {
        build();
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("find", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("find", collection, statement) : null;
        try {
            List<T> result = mongoTemplate.find(query, entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "find", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "find", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "find", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public T one() {
        build();
        query.limit(1);
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("find", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("find", collection, statement) : null;
        try {
            List<T> result = mongoTemplate.find(query, entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "find", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "find", System.currentTimeMillis() - start);
            return result.isEmpty() ? null : result.get(0);
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "find", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public Optional<T> oneOpt() {
        return Optional.ofNullable(one());
    }

    public T oneOrThrow(Supplier<? extends RuntimeException> exSupplier) {
        T t = one();
        if (t == null) throw exSupplier.get();
        return t;
    }

    public long count() {
        build();
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("count", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("count", collection, statement) : null;
        try {
            long result = mongoTemplate.count(query, entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "count", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "count", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "count", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public PageResult<T> pageResult() {
        build();
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("find", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("find", collection, statement) : null;
        try {
            Query countQuery = new Query();
            if (criteria != null) {
                countQuery.addCriteria(criteria);
            }
            long total = mongoTemplate.count(countQuery, entityClass);
            List<T> content = mongoTemplate.find(query, entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "find", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "find", System.currentTimeMillis() - start);
            int pageNum = pageRequest != null ? pageRequest.getPageNumber() + 1 : 1;
            int pageSize = pageRequest != null ? pageRequest.getPageSize() : (int) Math.min(total, 100);
            return new PageResult<>(content, total, pageNum, pageSize);
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "find", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    private void build() {
        if (used) throw new IllegalStateException("QueryBuilder can only be executed once");
        used = true;
        if (criteria != null) query.addCriteria(criteria);
        if (!selectFields.isEmpty()) {
            for (String field : selectFields) {
                query.fields().include(field);
            }
        }
        if (pageRequest != null) query.with(pageRequest);
        if (skip > 0) query.skip(skip);
        if (limit > 0) query.limit(limit);
        applyAnnotationFilters();
    }

    private void applyAnnotationFilters() {
        if (!ignoreSoftDelete) {
            String softDeleteField = entityIntrospector.getSoftDeleteField(entityClass);
            if (softDeleteField != null) {
                query.addCriteria(Criteria.where(softDeleteField).is(false));
            }
        }
        if (!bypassTenant) {
            String tenantField = entityIntrospector.getTenantField(entityClass);
            if (tenantField != null && TenantContext.get() != null) {
                query.addCriteria(Criteria.where(tenantField).is(TenantContext.get()));
            }
        }
    }
}