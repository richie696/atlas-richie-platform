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
package com.richie.component.mongodb.cache;

import com.mongodb.client.result.DeleteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 大对象缓存静态工具类（普通缓存请使用 richie-component-cache 组件的 GlobalCache）
 *
 * @author richie696
 * @version 1.2
 * @since 2021/12/05
 */
@Component
public class ObjectCache {

    private static final AtomicReference<ObjectCacheManager> MANAGE = new AtomicReference<>();

    private ObjectCache() {
    }

    /**
     * 添加对象缓存到指定集合中
     *
     * @param key        缓存集合键
     * @param cacheValue 缓存对象
     * @param <T>        缓存对象类型
     * @return 返回缓存对象
     */
    public static <T> T addObject(CollectionKey key, T cacheValue) {
        return MANAGE.get().addObject(key, cacheValue);
    }

    /**
     * 添加或合并对象缓存到指定集合中
     *
     * @param key         缓存集合键
     * @param cacheValues 缓存对象列表
     */
    public static <T> Collection<T> addObjects(CollectionKey key, List<T> cacheValues) {
        return MANAGE.get().addObjects(key, cacheValues);
    }

    /**
     * 合并对象缓存到指定集合中
     *
     * @param key        缓存集合键
     * @param cacheValue 缓存对象
     * @param <T>        缓存对象类型
     * @return 返回缓存对象
     */
    public static <T> T mergeObject(CollectionKey key, T cacheValue) {
        return MANAGE.get().mergeObject(key, cacheValue);
    }

    /**
     * 合并对象缓存到指定集合中
     *
     * @param key         缓存集合键
     * @param cacheValues 缓存对象列表
     * @param <T>         缓存对象类型
     * @return 返回缓存对象列表
     */
    public static <T> Collection<T> mergeObjects(CollectionKey key, List<T> cacheValues) {
        return MANAGE.get().mergeObjects(key, cacheValues);
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
    public static <T> T getObjectById(CollectionKey key, String documentId, Class<T> entityClass) {
        return MANAGE.get().getObjectById(key, documentId, entityClass);
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
    public static <T> T getObject(CollectionKey key, T condition, Class<T> entityClass) {
        return MANAGE.get().getObject(key, condition, entityClass);
    }

    /**
     * 获取指定集合中的所有缓存对象
     *
     * @param key         缓存集合键
     * @param entityClass 缓存对象类型
     * @param <T>         缓存对象类型
     * @return 返回缓存对象列表
     */
    public static <T> List<T> getObjects(CollectionKey key, T condition, Class<T> entityClass) {
        return MANAGE.get().getObjects(key, condition, entityClass);
    }

    /**
     * 获取指定集合中的所有缓存对象
     *
     * @param key         缓存集合键
     * @param entityClass 缓存对象类型
     * @param <T>         缓存对象类型
     * @return 返回缓存对象列表
     */
    public static <T> List<T> getAllObjects(CollectionKey key, Class<T> entityClass) {
        return MANAGE.get().getAllObjects(key, entityClass);
    }

    /**
     * 删除指定集合中的所有缓存对象
     *
     * @param key 缓存集合键
     */
    public static void removeObjects(CollectionKey key) {
        MANAGE.get().removeAllObjects(key);
    }

    /**
     * 删除指定集合中的缓存对象
     *
     * @param key         缓存集合键
     * @param condition   删除条件（注意：条件字段设置索引，以提升检索性能）
     * @param entityClass 缓存对象类型
     */
    public static DeleteResult removeObjects(CollectionKey key, Object condition, Class<?> entityClass) {
        return MANAGE.get().removeObjects(key, condition, entityClass);
    }

    /**
     * 获取指定集合中的文档数量。
     *
     * @param key 缓存集合键
     * @return 文档数量
     */
    public static long getCollectionCount(CollectionKey key) {
        return MANAGE.get().getCollectionCount(key);
    }

    /**
     * 初始化全局缓存工具类的方法（该接口由 Spring 调用）。
     *
     * @param objectCacheManager MongoDB 大对象缓存管理器
     */
    @Autowired
    public void setObjectCacheManager(ObjectCacheManager objectCacheManager) {
        if (ObjectCache.MANAGE.get() == null) {
            synchronized (ObjectCache.class) {
                if (ObjectCache.MANAGE.get() == null) {
                    ObjectCache.MANAGE.set(objectCacheManager);
                }
            }
        }
    }
}
