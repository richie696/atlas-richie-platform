package com.richie.component.mongodb;

import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.exception.DuplicateKeyException;
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

    public Mongodb(MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
        this.mongoTemplate = mongoTemplate;
        this.entityIntrospector = entityIntrospector;
    }

    public <T> QueryBuilder<T> query(Class<T> entityClass) {
        return new QueryBuilder<>(entityClass, mongoTemplate, entityIntrospector);
    }

    public <T> UpdateBuilder<T> update(Class<T> entityClass) {
        return new UpdateBuilder<>(entityClass, mongoTemplate, entityIntrospector);
    }

    public <T> DeleteBuilder<T> delete(Class<T> entityClass) {
        return new DeleteBuilder<>(entityClass, mongoTemplate, entityIntrospector);
    }

    public <T> T save(T entity) {
        return mongoTemplate.save(entity);
    }

    public <T> T insert(T entity) {
        try {
            return mongoTemplate.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            if (e.getCause() instanceof com.mongodb.DuplicateKeyException mongoEx) {
                throw DuplicateKeyException.wrap(mongoEx);
            }
            throw new DuplicateKeyException("Duplicate key", e);
        }
    }

    public <T> List<T> insertAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entities.get(0).getClass();
        return (List<T>) mongoTemplate.insert(entities, entityClass);
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
        mongoTemplate.dropCollection(entityClass);
    }
}
