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
package com.richie.component.cache.function;

import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Hash类型缓存管理器接口，定义了所有对 Redis Hash 类型的通用操作能力。
 * <p>适用于对象缓存、属性映射、分布式数据结构等场景。
 */
public interface HashFunction extends CacheFunction {
    /**
     * 防缓存击穿：Hash对象
     *
     * @param key      缓存key
     * @param clazz    缓存对象类型
     * @param dbLoader 回源加载器
     * @param timeout  超时时间
     * @param <T>      返回值类型
     * @return 返回缓存对象
     */
    <T> T getObjectFromHashWithLock(String key, Class<T> clazz, Supplier<T> dbLoader, long timeout);

    /**
     * 防缓存击穿：Hash单项
     *
     * @param key      资源键
     * @param hashKey  HASH资源键
     * @param clazz    目标缓存类型
     * @param dbLoader 回源加载器
     * @param timeout  超时时间
     * @param <T>      返回值类型
     * @return 返回资源值
     */
    <T> T getFromHashWithLock(String key, String hashKey, Class<T> clazz, Supplier<T> dbLoader, long timeout);

    /**
     * 防缓存击穿：Hash单项（支持复杂类型）
     *
     * @param key       资源键
     * @param hashKey   HASH资源键
     * @param reference 目标缓存类型引用
     * @param dbLoader  回源加载器
     * @param timeout   超时时间
     * @param <T>       返回值类型
     * @return 返回资源值
     */
    <T> T getFromHashWithLock(String key, String hashKey, TypeReference<T> reference, Supplier<T> dbLoader, long timeout);

    /**
     * 防缓存击穿：Hash对象（支持复杂类型）
     *
     * @param key       缓存key
     * @param reference 缓存对象类型引用
     * @param dbLoader  回源加载器
     * @param timeout   超时时间
     * @param <T>       返回值类型
     * @return 返回缓存对象
     */
    <T> T getObjectFromHashWithLock(String key, TypeReference<T> reference, Supplier<T> dbLoader, long timeout);

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @return 返回检查结果（true：存在，false：不存在）
     */
    boolean existsInHash(String key, String hashKey);

    /**
     * 根据HASH资源键获取资源HASH_KEY集合的方法
     *
     * @param key 资源键
     * @return 返回资源值集合
     */
    Set<String> getHashKeyList(String key);

    /**
     * 获取Hash对象（支持复杂类型）
     *
     * @param key       缓存key
     * @param reference 目标类型
     * @param <T>       返回值泛型类型
     * @return 返回对象
     */
    <T> T getObjectFromHash(String key, TypeReference<T> reference);

    /**
     * 获取Hash对象
     *
     * @param key       缓存key
     * @param valueClass 目标类型
     * @param <T> 目标对象类型
     * @return 返回对象
     */
    <T> T getObjectFromHash(String key, Class<T> valueClass);

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @param clazz   目标缓存类型
     * @param <T>     接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> T getFromHash(String key, String hashKey, Class<T> clazz);

    /**
     * 根据资源键获取资源值的方法（支持复杂类型）
     *
     * @param key       资源键
     * @param hashKey   HASH资源键
     * @param reference 目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> T getFromHash(String key, String hashKey, TypeReference<T> reference);

    /**
     * 根据资源键获取资源值的方法（批量获取，支持复杂类型）
     *
     * @param key       资源键
     * @param hashKeys  HASH资源键集合
     * @param reference 目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> List<T> getFromHash(String key, Collection<String> hashKeys, TypeReference<T> reference);

    /**
     * 获取Hash表所有数据（Map形式）
     *
     * @param key   资源键
     * @param clazz 目标类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回key到对象的映射
     */
    <T> Map<String, T> getAllMapFromHash(String key, Class<T> clazz);

    /**
     * 添加对象到缓存中的方法（Hash）
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void addObject(String key, Object value);

    /**
     * 添加对象到缓存中的方法（Hash）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间（单位：毫秒）
     */
    void addObject(String key, Object value, long timeout);

    /**
     * 刷新对象数据的方法
     *
     * @param key  缓存键
     * @param func 刷新缓存值的回调函数
     * @param <T>  回调的类型
     * @return 返回刷新结果
     */
    <T> T refreshObject(String key, UnaryOperator<T> func);

    /**
     * 批量添加缓存到Redis Hash的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    void batchAddToHash(Map<String, Map<String, ?>> map);

    /**
     * 批量添加集合数据到Redis hash表的方法
     *
     * @param key 集合key
     * @param map 批量添加的集合对象
     */
    void addHash(String key, Map<String, ?> map);

    /**
     * 添加键值对信息到Redis hash表的方法
     *
     * @param key       集合key
     * @param hashKey   hash表键
     * @param hashValue hash表值
     */
    void addHash(String key, String hashKey, Object hashValue);

    /**
     * 从指定Key中批量移除哈希表中指定键值对的方法
     *
     * @param key      元素KEY
     * @param hashKeys 批量移除的hashKey列表
     */
    void removeHashItem(String key, String... hashKeys);

    /**
     * 获取Hash表中元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    Long getHashSize(String key);

}
