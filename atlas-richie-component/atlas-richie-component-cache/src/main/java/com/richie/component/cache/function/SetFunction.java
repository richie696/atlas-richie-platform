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
package com.richie.component.cache.function;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Set类型缓存API管理器接口，封装了Redis中Set集合的常用操作。
 * <p>
 * 主要用于集合去重、批量操作、分布式缓存等场景，支持布隆过滤器防击穿、批量添加、差集、弹出等能力。
 * <ul>
 *   <li>getFromSetWithLock：防缓存击穿，带分布式锁的集合加载</li>
 *   <li>getFromSet：获取集合</li>
 *   <li>popDataFromSet/popMembersFromSet：弹出元素</li>
 *   <li>differenceFromSet/differenceAndStoreFromSet：集合差集操作</li>
 *   <li>existsInSet：判断元素是否存在</li>
 *   <li>addSet/batchAddToSet/addSetItem：批量添加</li>
 *   <li>removeSetItem：批量删除</li>
 *   <li>getSetSize：获取集合大小</li>
 * </ul>
 * <p>
 * 推荐用于用户标签、去重统计、批量缓存等高并发场景。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 11:00:00
 */
public interface SetFunction extends CacheFunction {
    /**
     * 防缓存击穿：Set
     *
     * @param key      缓存键
     * @param clazz    集合元素类型
     * @param dbLoader 回源加载器
     * @param timeout  超时时间（毫秒）
     * @param <T>      集合元素泛型类型
     * @return 集合值，不存在或加载失败时为 null
     */
    <T> Set<T> getFromSetWithLock(String key, Class<T> clazz, Supplier<Set<T>> dbLoader, long timeout);

    /**
     * 根据资源键获取资源值集合的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    <T> Set<T> getFromSet(String key, Class<T> clazz);

    /**
     * 从Set中弹出一个元素的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    <T> T popDataFromSet(String key, Class<T> clazz);

    /**
     * 从Set中弹出指定数量元素的方法
     *
     * @param key   资源键
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    <T> Set<T> popMembersFromSet(String key, long count, Class<T> clazz);

    /**
     * 查询给定的Set集合的差集并返回差集的方法
     *
     * @param keys  待比较的资源键列表
     * @param clazz 返回值元素类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    <T> Set<T> differenceFromSet(Collection<String> keys, Class<T> clazz);

    /**
     * 查询给定的Set集合的差分结果并返回的方法
     *
     * @param key       用于比较的主键
     * @param otherKeys 待比较的资源键列表
     * @param clazz     返回值元素类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    <T> Set<T> differenceFromSet(String key, Collection<String> otherKeys, Class<T> clazz);

    /**
     * 查询给定的Set集合的差集并保存到目标键位置的方法
     *
     * @param compareKeys 待比较的资源键列表
     * @param destKey     目标资源键
     * @return 返回目标资源键内的元素数量
     */
    Long differenceAndStoreFromSet(Collection<String> compareKeys, String destKey);

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key   资源键
     * @param value 资源值
     * @return 返回检查结果（true：存在，false：不存在）
     */
    boolean existsInSet(String key, Object value);

    /**
     * 批量添加缓存到Redis Set的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    void batchAddToSet(Map<String, Set<?>> map);

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key 列表名称
     * @param set 列表值
     */
    void addSet(String key, Set<?> set);

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     */
    void addSetItem(String key, Object... value);

    /**
     * 批量删除Set集合元素的方法
     *
     * @param key    列表名称
     * @param values 要移除的值
     */
    void removeSetItem(String key, Object... values);

    /**
     * 获取Set集合元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    Long getSetSize(String key);
}
