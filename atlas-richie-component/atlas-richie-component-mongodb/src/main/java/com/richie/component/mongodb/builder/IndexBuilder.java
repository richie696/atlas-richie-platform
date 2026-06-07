package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Component
public class IndexBuilder {
    private final MongoTemplate mongoTemplate;
    private final EntityIntrospector entityIntrospector;

    public IndexBuilder(MongoTemplate mongoTemplate, EntityIntrospector entityIntrospector) {
        this.mongoTemplate = mongoTemplate;
        this.entityIntrospector = entityIntrospector;
    }

    public void ensureIndexes(Class<?>... entityClasses) {
        for (Class<?> clazz : entityClasses) {
            ensureIndexesForClass(clazz);
        }
    }

    private void ensureIndexesForClass(Class<?> clazz) {
        var ops = mongoTemplate.indexOps(clazz);
        List<IndexDefinition> indexesToCreate = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Indexed.class)) {
                Indexed indexed = field.getAnnotation(Indexed.class);
                Index index = new Index();
                if (indexed.unique()) {
                    index.unique();
                }
                if (!indexed.name().isEmpty()) {
                    index.named(indexed.name());
                }
                Sort.Direction dir = indexed.direction() == IndexDirection.ASCENDING
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;
                index.on(field.getName(), dir);
                indexesToCreate.add(index);
            }
        }
        for (IndexDefinition indexDef : indexesToCreate) {
            ops.ensureIndex(indexDef);
        }
    }
}
