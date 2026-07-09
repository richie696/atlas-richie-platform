/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.callback;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;

/**
 * MongoDB查询结果转换回调
 *
 * @author richie696
 * @version 1.0
 * @since 2023-11-21 11:21:29
 */
@Slf4j
@Component
public class DataAfterConvertCallback<T> implements AfterConvertCallback<T> {

    /**
     * 查询结果从 Document 转为实体后回调，用于将 _id 等复合字段正确映射到实体（如 BigDecimal）。
     *
     * @param entity     已转换的实体
     * @param document   原始文档
     * @param collection 集合名
     * @return 处理后的实体
     */
    @Nonnull
    @Override
    public T onAfterConvert(@Nonnull T entity, @Nonnull Document document, @Nonnull String collection) {
        var id = document.get("_id");
        if (id instanceof Document groupDocument) {
            groupDocument.forEach((key, value) -> {
                try {
                    var field = getField(entity.getClass(), key);
                    if (field == null) {
                        log.error("{} 字段在 {} 中不存在。", key, entity.getClass().getName());
                        return;
                    }
                    field.setAccessible(true);
                    switch (value) {
                        case Double doubleValue when field.getType().equals(BigDecimal.class) ->
                                field.set(entity, BigDecimal.valueOf(doubleValue));
                        case Integer intValue when field.getType().equals(BigDecimal.class) ->
                                field.set(entity, BigDecimal.valueOf(intValue));
                        case Long longValue when field.getType().equals(BigDecimal.class) ->
                                field.set(entity, BigDecimal.valueOf(longValue));
                        case String stringValue when field.getType().equals(BigDecimal.class) ->
                                field.set(entity, new BigDecimal(stringValue));
                        case null, default -> field.set(entity, value);
                    }
                } catch (Exception e) {
                    log.error("反射设置{}值失败，值为：{}", key, value);
                }
            });
        }
        return entity;
    }

    /**
     * 按字段名查找类及其父类的声明字段。
     *
     * @param clazz     类
     * @param fieldName 字段名
     * @return 字段，未找到为 null
     */
    private Field getField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), fieldName);
            }
        }
        return null;
    }
}
