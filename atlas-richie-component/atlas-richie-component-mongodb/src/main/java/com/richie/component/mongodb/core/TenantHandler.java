package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.TenantScoped;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantHandler {

    private final Map<Class<?>, String> tenantFieldCache = new ConcurrentHashMap<>();

    public void addTenantCriteria(Query query, Class<?> entityClass) {
        String fieldName = getTenantField(entityClass);
        if (fieldName != null && TenantContext.get() != null) {
            query.addCriteria(Criteria.where(fieldName).is(TenantContext.get()));
        }
    }

    public void fillOnInsert(Object entity) {
        String fieldName = getTenantField(entity.getClass());
        if (fieldName != null && TenantContext.get() != null) {
            setField(entity, fieldName, TenantContext.get());
        }
    }

    public boolean bypass() {
        return false;
    }

    public String getTenantField(Class<?> entityClass) {
        return tenantFieldCache.computeIfAbsent(entityClass, this::resolveTenantField);
    }

    private String resolveTenantField(Class<?> clazz) {
        TenantScoped annotation = clazz.getAnnotation(TenantScoped.class);
        return annotation != null ? annotation.value() : null;
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(entity, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set tenant field: " + fieldName, e);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}