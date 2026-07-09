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