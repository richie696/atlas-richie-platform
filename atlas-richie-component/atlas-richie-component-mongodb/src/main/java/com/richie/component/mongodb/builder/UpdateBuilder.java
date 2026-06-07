package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.util.Collection;

public class UpdateBuilder<T> {
    private final Class<T> entityClass;
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;
    private final Query query = new Query();
    private Criteria criteria;
    private boolean criteriaStarted;
    private final Update update = new Update();
    private boolean used;

    public UpdateBuilder(Class<T> entityClass, MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
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
        return mongoTemplate.updateMulti(query, update, entityClass).getModifiedCount();
    }

    public T executeAndReturn() {
        if (used) throw new IllegalStateException("UpdateBuilder can only be executed once");
        used = true;
        if (criteria != null) query.addCriteria(criteria);
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), entityClass);
    }
}
