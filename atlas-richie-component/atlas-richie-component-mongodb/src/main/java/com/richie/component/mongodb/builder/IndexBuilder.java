/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.annotation.ExpireAfter;
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
import java.util.concurrent.TimeUnit;

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

        // Use EntityIntrospector (cached) instead of manual reflection
        for (Field field : entityIntrospector.getIndexedFields(clazz)) {
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

        for (Field field : entityIntrospector.getExpireAfterFields(clazz)) {
            ExpireAfter expireAfter = field.getAnnotation(ExpireAfter.class);
            Index index = new Index().on(field.getName(), Sort.Direction.ASC)
                    .expire(expireAfter.seconds(), TimeUnit.SECONDS);
            indexesToCreate.add(index);
        }

        for (IndexDefinition indexDef : indexesToCreate) {
            ops.createIndex(indexDef);
        }
    }
}