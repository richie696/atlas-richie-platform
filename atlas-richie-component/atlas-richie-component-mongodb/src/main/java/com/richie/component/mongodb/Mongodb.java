/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb;

import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;
import com.richie.component.mongodb.core.AuditFieldHandler;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantHandler;
import com.richie.component.mongodb.exception.DuplicateKeyException;
import com.richie.component.mongodb.observability.MongodbMetricsRecorder;
import com.richie.component.mongodb.observability.MongodbSlowQueryLogger;
import com.richie.component.mongodb.observability.MongodbTracing;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class Mongodb {
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;
    private final AuditFieldHandler auditFieldHandler;
    private final TenantHandler tenantHandler;
    private final MongodbTracing tracing;
    private final MongodbMetricsRecorder metricsRecorder;
    private final MongodbSlowQueryLogger slowQueryLogger;

    public Mongodb(MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector,
                   AuditFieldHandler auditFieldHandler, TenantHandler tenantHandler,
                   MongodbTracing tracing, MongodbMetricsRecorder metricsRecorder,
                   MongodbSlowQueryLogger slowQueryLogger) {
        this.mongoTemplate = mongoTemplate;
        this.entityIntrospector = entityIntrospector;
        this.auditFieldHandler = auditFieldHandler;
        this.tenantHandler = tenantHandler;
        this.tracing = tracing;
        this.metricsRecorder = metricsRecorder;
        this.slowQueryLogger = slowQueryLogger;
    }

    public <T> QueryBuilder<T> query(Class<T> entityClass) {
        return new QueryBuilder<>(entityClass, mongoTemplate, entityIntrospector, tracing, metricsRecorder, slowQueryLogger);
    }

    public <T> UpdateBuilder<T> update(Class<T> entityClass) {
        return new UpdateBuilder<>(entityClass, mongoTemplate, entityIntrospector, tracing, metricsRecorder, slowQueryLogger);
    }

    public <T> DeleteBuilder<T> delete(Class<T> entityClass) {
        return new DeleteBuilder<>(entityClass, mongoTemplate, entityIntrospector, tracing, metricsRecorder, slowQueryLogger);
    }

    public <T> T save(T entity) {
        tenantHandler.fillOnInsert(entity);
        auditFieldHandler.fillOnInsert(entity);
        String collection = entityIntrospector.getCollectionName(entity.getClass());
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("save", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("save", collection, entity.getClass().getSimpleName()) : null;
        try {
            T result = mongoTemplate.save(entity);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "save", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "save", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "save", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) MongodbTracing.recordError(scope.getSpan(), t);
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public <T> T insert(T entity) {
        tenantHandler.fillOnInsert(entity);
        auditFieldHandler.fillOnInsert(entity);
        String collection = entityIntrospector.getCollectionName(entity.getClass());
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("insert", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("insert", collection, entity.getClass().getSimpleName()) : null;
        try {
            T result = mongoTemplate.insert(entity);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "insert", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "insert", System.currentTimeMillis() - start);
            return result;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            if (sample != null) metricsRecorder.stop(sample, "insert", collection, false);
            if (sample != null) metricsRecorder.recordError(e);
            if (scope != null) MongodbTracing.recordError(scope.getSpan(), e);
            if (e.getCause() instanceof com.mongodb.DuplicateKeyException mongoEx) {
                throw DuplicateKeyException.wrap(mongoEx);
            }
            throw new DuplicateKeyException("Duplicate key", e);
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "insert", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) MongodbTracing.recordError(scope.getSpan(), t);
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public <T> List<T> insertAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entities.get(0).getClass();
        String collection = entityIntrospector.getCollectionName(entityClass);
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("insert", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("insert", collection, "List<" + entityClass.getSimpleName() + ">") : null;
        try {
            List<T> result = (List<T>) mongoTemplate.insert(entities, entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "insert", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "insert", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "insert", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) MongodbTracing.recordError(scope.getSpan(), t);
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }

    public <T> Optional<T> findById(Class<T> entityClass, Object id) {
        T result = mongoTemplate.findById(id, entityClass);
        return Optional.ofNullable(result);
    }

    public <T> T findByIdOrThrow(Class<T> entityClass, Object id, java.util.function.Supplier<? extends RuntimeException> exSupplier) {
        T result = mongoTemplate.findById(id, entityClass);
        if (result == null) {
            throw exSupplier.get();
        }
        return result;
    }

    public boolean existsById(Class<?> entityClass, Object id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        return mongoTemplate.exists(query, entityClass);
    }

    public void deleteById(Class<?> entityClass, Object id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, entityClass);
    }

    public void dropCollection(Class<?> entityClass) {
        String collection = entityIntrospector.getCollectionName(entityClass);
        Timer.Sample sample = metricsRecorder != null ? metricsRecorder.start("drop", collection) : null;
        long start = System.currentTimeMillis();
        MongodbTracing.TracingScope scope = tracing != null ? MongodbTracing.createSpan("drop", collection, entityClass.getSimpleName()) : null;
        try {
            mongoTemplate.dropCollection(entityClass);
            if (scope != null) MongodbTracing.recordSuccess(scope.getSpan(), System.currentTimeMillis() - start);
            if (sample != null) metricsRecorder.stop(sample, "drop", collection, true);
            if (slowQueryLogger != null) slowQueryLogger.logIfSlow(collection, "drop", System.currentTimeMillis() - start);
        } catch (Throwable t) {
            if (sample != null) metricsRecorder.stop(sample, "drop", collection, false);
            if (sample != null) metricsRecorder.recordError(t);
            if (scope != null) MongodbTracing.recordError(scope.getSpan(), t);
            throw t;
        } finally {
            if (scope != null) scope.close();
        }
    }
}
