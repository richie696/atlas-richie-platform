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
package com.richie.component.cache.function;

import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 字符串缓存操作接口，提供对Redis String类型缓存的操作方法。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 16:57:42
 */
public interface StringFunction extends CacheFunction {
    /**
     * 防缓存击穿：String类型
     *
     * @param key      缓存键
     * @param dbLoader 回源加载器
     * @param timeout  超时时间（毫秒）
     * @return 缓存值，不存在或加载失败时为 null
     */
    String getFromStringWithLock(String key, Supplier<String> dbLoader, long timeout);

    /**
     * 批量添加缓存到Redis String中的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    void batchAddToString(Map<String, ?> map);

    /**
     * 批量添加缓存到Redis String中的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map     批量添加的缓存数据
     * @param timeout 超时时间（单位：毫秒）
     */
    void batchAddToString(Map<String, ?> map, long timeout);

    /**
     * 添加缓存到Redis String中的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间（单位：毫秒）
     */
    void addValue(String key, Object value, long timeout);

    /**
     * 添加缓存到Redis String中的方法
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void addValue(String key, Object value);

    /**
     * 添加缓存到Redis String中的方法（如果不存在）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回添加结果
     */
    boolean addValueIfAbsent(String key, Object value, long timeout);

    /**
     * 添加缓存到Redis String中的方法（如果不存在）
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回添加结果
     */
    boolean addValueIfAbsent(String key, Object value);

    /**
     * 计数器+1的方法
     *
     * @param key     缓存键
     * @param timeout 超时时间
     * @return 返回最新地计数值
     */
    Long increment(String key, Long timeout);

    /**
     * 批量更新缓存对象的方法
     *
     * @param batchUpdate 批量更新的数据
     * @param timeout     超时时间
     */
    void batchUpdateIfAbsent(Map<String, ?> batchUpdate, Long timeout);

    /**
     * 计数器+1的方法
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间
     * @return 返回最新地计数值
     */
    Long increment(String key, long delta, Long timeout);

    /**
     * 计数器+1的方法
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间
     * @return 返回最新地计数值
     */
    Double increment(String key, double delta, Long timeout);

    /**
     * 计数器-1的方法
     *
     * @param key     缓存键
     * @param timeout 超时时间
     * @return 返回最新地计数值
     */
    long decrement(String key, Long timeout);

    /**
     * 计数器-1的方法
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间
     * @return 返回最新地计数值
     */
    long decrement(String key, long delta, Long timeout);

    /**
     * 获取匹配的 KEY 对应值的方法
     * <p style="color: red">（注：此方法可能会破坏分布式锁对值的锁定，慎用！）
     *
     * @param keys      匹配的 KEY 集合
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回 KEY 对应的值，如果某个值不存在则返回null
     */
    <T> Map<String, T> getValueMap(List<String> keys, TypeReference<T> reference);

    /**
     * 获取匹配的 KEY 对应值的方法
     * <p style="color: red">（注：此方法可能会破坏分布式锁对值的锁定，慎用！）
     *
     * @param keys      匹配的 KEY 集合
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回 KEY 对应的值，如果某个值不存在则返回null
     */
    <T> List<T> getObjects(Collection<String> keys, TypeReference<T> reference);

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> T getFromString(String key, Class<T> clazz);

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key       资源键
     * @param reference 目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> T getFromString(String key, TypeReference<T> reference);

    /**
     * 模糊匹配获取所有值的方法
     *
     * @param match 模糊匹配的key
     * @param count 每次扫描的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    <T> Map<String, T> scan(String match, int count, Class<T> clazz);
}
