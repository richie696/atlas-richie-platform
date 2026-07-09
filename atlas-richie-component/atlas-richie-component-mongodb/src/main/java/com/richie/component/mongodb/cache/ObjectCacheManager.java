/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.cache;

import com.mongodb.client.result.DeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MongoDB 大对象缓存管理器，为 ObjectCache 静态工具提供底层 MongoTemplate 操作。
 *
 * @author richie696
 * @since 2021/12/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectCacheManager {

    /** Spring Data MongoDB 操作模板 */
    private final MongoTemplate template;

    /**
     * 添加对象缓存到指定集合中
     *
     * @param key        缓存集合键
     * @param cacheValue 缓存对象
     * @param <T>        缓存对象类型
     * @return 返回缓存对象
     */
    public <T> T addObject(CollectionKey key, T cacheValue) {
        return template.insert(cacheValue, key.getKey());
    }

    /**
     * 添加或合并对象缓存到指定集合中
     *
     * @param key         缓存集合键
     * @param cacheValues 缓存对象列表
     */
    public <T> Collection<T> addObjects(CollectionKey key, List<T> cacheValues) {
        return template.insert(cacheValues, key.getKey());
    }

    /**
     * 合并对象缓存到指定集合中
     *
     * @param key        缓存集合键
     * @param cacheValue 缓存对象
     * @param <T>        缓存对象类型
     * @return 返回缓存对象
     */
    public <T> T mergeObject(CollectionKey key, T cacheValue) {
        return template.save(cacheValue, key.getKey());
    }

    /**
     * 合并对象缓存到指定集合中
     *
     * @param key         缓存集合键
     * @param cacheValues 缓存对象列表
     * @param <T>         缓存对象类型
     * @return 返回缓存对象列表
     */
    public <T> Collection<T> mergeObjects(CollectionKey key, List<T> cacheValues) {
        return cacheValues.stream().map(cacheValue -> template.save(cacheValue, key.getKey())).collect(Collectors.toList());
    }

    /**
     * 根据文档ID获取指定集合中的缓存对象
     *
     * @param key         缓存集合键
     * @param documentId  文档ID
     * @param entityClass 缓存对象类型
     * @param <T>         缓存对象类型
     * @return 返回缓存对象
     */
    public <T> T getObjectById(CollectionKey key, String documentId, Class<T> entityClass) {
        return template.findById(documentId, entityClass, key.getKey());
    }

    /**
     * 获取指定集合中的缓存对象
     *
     * @param key         缓存集合键
     * @param condition   查询条件
     * @param entityClass 缓存对象类型
     * @param <T>         缓存对象类型
     * @return 返回缓存对象
     */
    public <T> T getObject(CollectionKey key, Object condition, Class<T> entityClass) {
        Query query = new Query();
        ReflectionUtils.doWithFields(entityClass, field -> {
            field.setAccessible(true);
            Object value = field.get(condition);
            if (value != null) {
                query.addCriteria(Criteria.where(field.getName()).is(value));
            }
        });
        return template.findOne(query, entityClass, key.getKey());
    }

    /**
     * 获取指定集合中的所有缓存对象
     *
     * @param key         缓存集合键
     * @param entityClass 缓存对象类型
     * @param <T>         缓存对象类型
     * @return 返回缓存对象列表
     */
    public <T> List<T> getAllObjects(CollectionKey key, Class<T> entityClass) {
        return template.findAll(entityClass, key.getKey());
    }

    /**
     * 获取指定集合中的所有缓存对象
     *
     * @param key         缓存集合键
     * @param entityClass 缓存对象类型
     * @param <T>         缓存对象类型
     * @return 返回缓存对象列表
     */
    public <T> List<T> getObjects(CollectionKey key, T condition, Class<T> entityClass) {
        // 通过反射获取对象中的非空字段，并使用fieldName作为查询条件创建Criteria
        Query query = new Query();
        ReflectionUtils.doWithFields(entityClass, field -> {
            field.setAccessible(true);
            Object value = field.get(condition);
            if (value != null) {
                query.addCriteria(Criteria.where(field.getName()).is(value));
            }
        });
        return template.find(query, entityClass, key.getKey());
    }

    /**
     * 删除指定集合中的所有缓存对象
     *
     * @param key 缓存集合键
     */
    public void removeAllObjects(CollectionKey key) {
        template.dropCollection(key.getKey());
    }

    /**
     * 删除指定集合中的缓存对象
     *
     * @param key         缓存集合键
     * @param condition   删除条件（注意：条件字段设置索引，以提升检索性能）
     * @param entityClass 缓存对象类型
     */
    public DeleteResult removeObjects(CollectionKey key, Object condition, Class<?> entityClass) {
        Query query = new Query();
        ReflectionUtils.doWithFields(entityClass, field -> {
            field.setAccessible(true);
            Object value = field.get(condition);
            if (value != null) {
                query.addCriteria(Criteria.where(field.getName()).is(value));
            }
        });
        return template.remove(query, entityClass, key.getKey());
    }

    /**
     * 获取指定集合中的缓存对象数量
     *
     * @param key 缓存集合键
     * @return 返回缓存对象数量
     */
    public long getCollectionCount(CollectionKey key) {
        return template.count(new Query(), key.getKey());
    }


    /**
     * 根据条件获取指定集合中的缓存对象数量
     *
     * @param key 缓存集合键
     * @param condition 查询条件
     * @param entityClass 缓存对象类型
     * @return 返回缓存对象数量
     */
    public long getCollectionCount(CollectionKey key, Object condition, Class<?> entityClass) {
        Query query = new Query();
        ReflectionUtils.doWithFields(entityClass, field -> {
            field.setAccessible(true);
            Object value = field.get(condition);
            if (value != null) {
                query.addCriteria(Criteria.where(field.getName()).is(value));
            }
        });
        return template.count(query, key.getKey());
    }

}
