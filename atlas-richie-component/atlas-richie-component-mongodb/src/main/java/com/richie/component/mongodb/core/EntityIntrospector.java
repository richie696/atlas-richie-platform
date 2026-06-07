package com.richie.component.mongodb.core;

import com.richie.component.mongodb.builder.LambdaField;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EntityIntrospector {

    private final Map<Class<?>, EntityMeta> cache = new ConcurrentHashMap<>();

    public String getCollectionName(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, this::buildMeta).collectionName;
    }

    public String getIdFieldName(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, this::buildMeta).idField;
    }

    public List<Field> getIndexedFields(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, this::buildMeta).indexedFields;
    }

    public String resolveFieldName(LambdaField<?, ?> lambda) {
        return LambdaMeta.resolveFieldName(lambda);
    }

    private EntityMeta buildMeta(Class<?> clazz) {
        Document doc = clazz.getAnnotation(Document.class);
        String collectionName = doc != null && !doc.value().isEmpty()
                ? doc.value()
                : clazz.getSimpleName().toLowerCase();

        String idField = "id";
        List<Field> indexedFields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idField = field.getName();
            }
            if (field.isAnnotationPresent(Indexed.class)) {
                indexedFields.add(field);
            }
        }

        return new EntityMeta(collectionName, idField, indexedFields);
    }

    record EntityMeta(String collectionName, String idField, List<Field> indexedFields) {}
}
