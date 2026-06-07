package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.AuditContext;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.observability.MongodbMetricsRecorder;
import com.richie.component.mongodb.observability.MongodbSlowQueryLogger;
import com.richie.component.mongodb.observability.MongodbTracing;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Instant;
import java.util.Collection;

public class UpdateBuilder<T> {
    private final Class<T> entityClass;
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;
    private final MongodbTracing tracing;
    private final MongodbMetricsRecorder metricsRecorder;
    private final MongodbSlowQueryLogger slowQueryLogger;
    private final Query query = new Query();
    private Criteria criteria;
    private boolean criteriaStarted;
    private final Update update = new Update();
    private boolean used;
    private final AuditContext auditContext;

    public UpdateBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
        this(entityClass, mongoTemplate, entityIntrospector, new AuditContext(), null, null, null);
    }

    public UpdateBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector, AuditContext auditContext) {
        this(entityClass, mongoTemplate, entityIntrospector, auditContext, null, null, null);
    }

    public UpdateBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector,
                         MongodbTracing tracing, MongodbMetricsRecorder metricsRecorder, MongodbSlowQueryLogger slowQueryLogger) {
        this(entityClass, mongoTemplate, entityIntrospector, new AuditContext(), tracing, metricsRecorder, slowQueryLogger);
    }

    public UpdateBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector,
                         AuditContext auditContext, MongodbTracing tracing, MongodbMetricsRecorder metricsRecorder,
                         MongodbSlowQueryLogger slowQueryLogger) {
        this.entityClass = entityClass;
        this.mongoTemplate = mongoTemplate;
        this.entityIntrospector = entityIntrospector;
        this.auditContext = auditContext;
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

    public UpdateBuilder<T> eq(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).is(value);
        return this;
    }

    public UpdateBuilder<T> eq(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) eq(field, value);
        return this;
    }

    public UpdateBuilder<T> gt(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).gt(value);
        return this;
    }

    public UpdateBuilder<T> ge(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).gte(value);
        return this;
    }

    public UpdateBuilder<T> lt(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).lt(value);
        return this;
    }

    public UpdateBuilder<T> le(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).lte(value);
        return this;
    }

    public UpdateBuilder<T> in(LambdaField<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).in(values);
        return this;
    }

    public UpdateBuilder<T> nin(LambdaField<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).nin(values);
        return this;
    }

    public UpdateBuilder<T> exists(LambdaField<T, ?> field) {
        startCriteria();
        criteria.and(toFieldName(field)).exists(true);
        return this;
    }

    public UpdateBuilder<T> set(LambdaField<T, ?> field, Object value) {
        update.set(toFieldName(field), value);
        return this;
    }

    public UpdateBuilder<T> inc(LambdaField<T, ?> field, Number value) {
        update.inc(toFieldName(field), value);
        return this;
    }

    public UpdateBuilder<T> unset(LambdaField<T, ?> field) {
        update.unset(toFieldName(field));
        return this;
    }

    public UpdateBuilder<T> push(LambdaField<T, ?> field, Object value) {
        update.push(toFieldName(field), value);
        return this;
    }

    public UpdateBuilder<T> pull(LambdaField<T, ?> field, Object value) {
        update.pull(toFieldName(field), value);
        return this;
    }

    public UpdateBuilder<T> addToSet(LambdaField<T, ?> field, Object value) {
        update.addToSet(toFieldName(field), value);
        return this;
    }

    public UpdateBuilder<T> rename(LambdaField<T, ?> oldField, LambdaField<T, ?> newField) {
        update.rename(toFieldName(oldField), toFieldName(newField));
        return this;
    }

    public long execute() {
        if (used) throw new IllegalStateException("UpdateBuilder can only be executed once");
        used = true;
        if (criteria != null) query.addCriteria(criteria);
        appendAuditFields();
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("update", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("update", collection, statement) : null;
        try {
            long result = mongoTemplate.updateMulti(query, update, entityClass).getModifiedCount();
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "update", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "update", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "update", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public T executeAndReturn() {
        if (used) throw new IllegalStateException("UpdateBuilder can only be executed once");
        used = true;
        if (criteria != null) query.addCriteria(criteria);
        appendAuditFields();
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("update", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("update", collection, statement) : null;
        try {
            T result = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "update", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "update", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "update", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    private void appendAuditFields() {
        if (entityIntrospector.hasAuditFields(entityClass)) {
            update.set("updatedAt", Instant.now());
            update.set("updatedBy", auditContext.currentUser());
        }
    }
}