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

import tools.jackson.core.type.TypeReference;

import java.util.*;

/**
 * ZSet类型缓存API管理器接口，封装了Redis中ZSet有序集合的常用操作。
 * <p>
 * 主要用于排行榜、分数排序、批量操作、集合运算等场景，支持批量添加、弹出、分数增减、交并差集等能力。
 * <ul>
 *   <li>batchAddToZSet：批量添加</li>
 *   <li>popMinFromZSet：弹出队首元素</li>
 *   <li>addZSet/addZSetItem：添加元素</li>
 *   <li>removeZSetItem：删除元素/区间</li>
 *   <li>reverseRangeWithScores/reverseRangeByScore：区间查询</li>
 *   <li>getZSetSize/incrementScore/getZSetRank/getZSetReverseRank：统计与分数操作</li>
 *   <li>getZSetData：区间数据获取</li>
 *   <li>intersectFromZSet/unionFromZSet/differenceFromZSet：集合运算</li>
 *   <li>intersectAndStoreFromZSet/unionAndStoreFromZSet/differenceAndStoreFromZSet：集合运算并存储</li>
 * </ul>
 * <p>
 * 推荐用于积分榜、活跃度排行、分数统计、复杂集合运算等高并发场景。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 11:10:00
 */
public interface ZSetFunction extends CacheFunction {
    /**
     * 批量添加缓存到Redis Set的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    void batchAddToZSet(Map<String, TreeSet<?>> map);

    /**
     * 弹出ZSet队首元素的方法
     *
     * @param key       列表名称
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 队首元素，无元素时为 null
     */
    <T> T popMinFromZSet(String key, TypeReference<T> reference);

    /**
     * 弹出ZSet队首元素的方法
     *
     * @param key       列表名称
     * @param count     弹出元素的数量
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 弹出的元素集合
     */
    <T> Set<T> popMinFromZSet(String key, long count, TypeReference<T> reference);

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key      列表名称
     * @param orderSet 列表值
     */
    void addZSet(String key, TreeSet<?> orderSet);

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     * @param score 列表排序号
     */
    void addZSetItem(String key, Object value, double score);

    /**
     * 批量删除Set集合元素的方法
     *
     * @param key    列表名称
     * @param values 要移除的值
     */
    void removeZSetItem(String key, Object... values);

    /**
     * 删除指定范围的元素
     *
     * @param key   列表名称
     * @param start 要移除的元素起始位置
     * @param end   要移除的元素结束位置
     */
    void removeZSetItem(String key, long start, long end);

    /**
     * 根据元素排序分数删除指定范围元素的方法
     *
     * @param key 缓存KEY
     * @param min 最小分数
     * @param max 最大分数
     */
    void removeZSetItem(String key, double min, double max);

    /**
     * 以Score值降序排列获取指定Rank范围元素的方法
     *
     * @param key       资源KEY
     * @param start     起始索引位置
     * @param end       结束索引位置
     * @param reference 目标元素类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回指定范围内的元素列表
     */
    <T> Set<T> reverseRangeWithScores(String key, long start, long end, TypeReference<T> reference);

    /**
     * 以Score值降序排列获取指定范围元素的方法
     *
     * @param key      资源KEY
     * @param minScore 最小排序值
     * @param maxScore 最大排序值
     * @param reference 目标类型引用
     * @param <T>      接收返回值使用的泛型类型
     * @return 返回指定范围内的元素列表
     */
    <T> Set<T> reverseRangeByScore(String key, double minScore, double maxScore, TypeReference<T> reference);

    /**
     * 获取ZSet集合元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    Long getZSetSize(String key);

    /**
     * 增加ZSet集合元素的分数的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @param delta 增量
     * @return 返回增加后的分数
     */
    Double incrementScore(String key, Object value, double delta);

    /**
     * 获取ZSet集合元素的排名的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回元素的排名
     */
    Long getZSetRank(String key, Object value);

    /**
     * 获取ZSet集合元素在反转排序后排名的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回元素的排名
     */
    Long getZSetReverseRank(String key, Object value);

    /**
     * 获取ZSet集合全部元素的的方法
     *
     * @param key       元素KEY
     * @param start     元素起始位置
     * @param end       元素结束位置
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回全部的元素
     */
    <T> Map<Double, T> getZSetData(String key, long start, long end, TypeReference<T> reference);

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的交集并返回的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param clazz     目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返会并集的元素数量
     */
    <T> NavigableSet<T> intersectFromZSet(String key, Collection<String> otherKeys, Class<T> clazz);

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的并集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param clazz     目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返会并集的元素数量
     */
    <T> NavigableSet<T> unionFromZSet(String key, Collection<String> otherKeys, Class<T> clazz);

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的并集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param destKey   目标元素KEY
     * @return 返会并集的元素数量
     */
    Long unionAndStoreFromZSet(String key, Collection<String> otherKeys, String destKey);

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的交集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param destKey   目标元素KEY
     * @return 返会交集的元素数量
     */
    Long intersectAndStoreFromZSet(String key, Collection<String> otherKeys, String destKey);

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的差集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param destKey   目标元素KEY
     * @return 返会交集的元素数量
     */
    Long differenceAndStoreFromZSet(String key, Collection<String> otherKeys, String destKey);

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的差集的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param clazz     目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返会交集的元素数量
     */
    <T> NavigableSet<T> differenceFromZSet(String key, Collection<String> otherKeys, Class<T> clazz);
}
