package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.util.Collection;

public class DeleteBuilder<T> {
    private final Class<T> entityClass;
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;
    private final Query query = new Query();
    private Criteria criteria;
    private boolean criteriaStarted;
    private boolean used;
    private boolean force = false;

    public DeleteBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
        this.entityClass = entityClass;
        this.mongoTemplate = mongoTemplate;
        this.entityIntrospector = entityIntrospector;
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
        if (!force && softDeleteField != null) {
            query.addCriteria(Criteria.where(softDeleteField).is(false));
            Update update = Update.update(softDeleteField, true);
            return mongoTemplate.updateMulti(query, update, entityClass).getModifiedCount();
        }
        return mongoTemplate.remove(query, entityClass).getDeletedCount();
    }
}