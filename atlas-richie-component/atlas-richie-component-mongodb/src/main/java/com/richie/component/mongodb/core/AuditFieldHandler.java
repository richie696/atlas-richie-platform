package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.AuditFields;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;
import java.time.Instant;

/**
 * 插入和更新操作时审计字段填充的处理器。
 *
 * @author Richie
 */
@Component
public class AuditFieldHandler {

    private static final String CREATED_AT = "createdAt";
    private static final String CREATED_BY = "createdBy";
    private static final String UPDATED_AT = "updatedAt";
    private static final String UPDATED_BY = "updatedBy";

    public void fillOnInsert(Object entity) {
        if (!hasAuditFields(entity.getClass())) {
            return;
        }
        Instant now = Instant.now();
        String currentUser = new AuditContext().currentUser();
        setField(entity, CREATED_AT, now);
        setField(entity, CREATED_BY, currentUser);
        setField(entity, UPDATED_AT, now);
        setField(entity, UPDATED_BY, currentUser);
    }

    public void appendOnUpdate(Update update, Class<?> entityClass) {
        if (!hasAuditFields(entityClass)) {
            return;
        }
        Instant now = Instant.now();
        String currentUser = new AuditContext().currentUser();
        update.set(UPDATED_AT, now);
        update.set(UPDATED_BY, currentUser);
    }

    public boolean hasAuditFields(Class<?> entityClass) {
        return entityClass.isAnnotationPresent(AuditFields.class);
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(entity, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set audit field: " + fieldName, e);
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