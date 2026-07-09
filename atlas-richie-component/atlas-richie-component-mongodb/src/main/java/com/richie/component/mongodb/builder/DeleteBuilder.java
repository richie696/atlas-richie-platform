/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.observability.MongodbMetricsRecorder;
import com.richie.component.mongodb.observability.MongodbSlowQueryLogger;
import com.richie.component.mongodb.observability.MongodbTracing;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.util.Collection;

public class DeleteBuilder<T> {
    private final Class<T> entityClass;
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;
    private final MongodbTracing tracing;
    private final MongodbMetricsRecorder metricsRecorder;
    private final MongodbSlowQueryLogger slowQueryLogger;
    private final Query query = new Query();
    private Criteria criteria;
    private boolean criteriaStarted;
    private boolean used;
    private boolean force = false;

    public DeleteBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
        this(entityClass, mongoTemplate, entityIntrospector, null, null, null);
    }

    public DeleteBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector,
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

    public DeleteBuilder<T> eq(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).is(value);
        return this;
    }

    public DeleteBuilder<T> eq(boolean condition, LambdaField<T, ?> field, Object value) {
        if (condition) eq(field, value);
        return this;
    }

    public DeleteBuilder<T> gt(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).gt(value);
        return this;
    }

    public DeleteBuilder<T> ge(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).gte(value);
        return this;
    }

    public DeleteBuilder<T> lt(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).lt(value);
        return this;
    }

    public DeleteBuilder<T> le(LambdaField<T, ?> field, Object value) {
        if (value == null) return this;
        startCriteria();
        criteria.and(toFieldName(field)).lte(value);
        return this;
    }

    public DeleteBuilder<T> in(LambdaField<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).in(values);
        return this;
    }

    public DeleteBuilder<T> nin(LambdaField<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        startCriteria();
        criteria.and(toFieldName(field)).nin(values);
        return this;
    }

    public DeleteBuilder<T> exists(LambdaField<T, ?> field) {
        startCriteria();
        criteria.and(toFieldName(field)).exists(true);
        return this;
    }

    public DeleteBuilder<T> force() {
        this.force = true;
        return this;
    }

    public long execute() {
        if (used) throw new IllegalStateException("DeleteBuilder can only be executed once");
        used = true;
        if (criteria != null) query.addCriteria(criteria);
        String softDeleteField = entityIntrospector.getSoftDeleteField(entityClass);
        String collection = entityIntrospector.getCollectionName(entityClass);
        String statement = query.toString();
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("delete", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("delete", collection, statement) : null;
        try {
            long result;
            if (!force && softDeleteField != null) {
                query.addCriteria(Criteria.where(softDeleteField).is(false));
                Update update = Update.update(softDeleteField, true);
                result = mongoTemplate.updateMulti(query, update, entityClass).getModifiedCount();
            } else {
                result = mongoTemplate.remove(query, entityClass).getDeletedCount();
            }
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "delete", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "delete", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "delete", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) {
                MongodbTracing.recordError(scope.getSpan(), t);
            }
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }
}