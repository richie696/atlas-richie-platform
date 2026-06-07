package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.SoftDelete;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 软删除操作和查询过滤的处理器。
 *
 * @author Richie
 */
@Component
public class SoftDeleteHandler {

    private final Map<Class<?>, String> softDeleteFieldCache = new ConcurrentHashMap<>();

    public void addNotDeletedCriteria(Query query, Class<?> entityClass) {
        String fieldName = getSoftDeleteField(entityClass);
        if (fieldName != null) {
            query.addCriteria(Criteria.where(fieldName).is(false));
        }
    }

    public Update markAsDeleted(Class<?> entityClass) {
        String fieldName = getSoftDeleteField(entityClass);
        if (fieldName == null) {
            return new Update();
        }
        return Update.update(fieldName, true);
    }

    public String getSoftDeleteField(Class<?> entityClass) {
        return softDeleteFieldCache.computeIfAbsent(entityClass, this::resolveSoftDeleteField);
    }

    private String resolveSoftDeleteField(Class<?> clazz) {
        SoftDelete annotation = clazz.getAnnotation(SoftDelete.class);
        return annotation != null ? annotation.value() : null;
    }
}