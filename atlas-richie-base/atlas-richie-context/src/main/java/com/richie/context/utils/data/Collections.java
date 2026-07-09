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
package com.richie.context.utils.data;

import com.google.common.collect.Lists;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 集合框架工具类
 *
 * @author richie696
 * @version 1.1
 * @since 2023-08-23 13:15:02
 */
@SuppressWarnings("unused")
public final class Collections {
    private static final int EXPAND_FACTOR = 2;

    private Collections() {
    }

    /**
     * 将枚举接口类型转换成流
     *
     * @param enumeration 需要转换成流的枚举对象
     * @param <T>         泛型类型
     * @return 返回转换之后的 Stream 对象
     */
    public static <T> Stream<T> streamOf(Enumeration<T> enumeration) {
        if (enumeration == null) {
            return Stream.empty();
        }
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(enumeration.asIterator(), Spliterator.ORDERED),
                false
        );
    }

    /**
     * 创建一个空的可修改的Map集合
     *
     * @return 返回一个空的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf() {
        return mapN(false);
    }

    /**
     * 创建一个包含1个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @return 返回一个包含一个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        return mapN(false, k1, v1);
    }

    /**
     * 创建一个包含2个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @return 返回一个包含一个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return mapN(false, k1, v1, k2, v2);
    }

    /**
     * 创建一个包含3个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @return 返回一个包含3个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        return mapN(false, k1, v1, k2, v2, k3, v3);
    }

    /**
     * 创建一个包含4个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @return 返回一个包含4个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4);
    }

    /**
     * 创建一个包含5个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @return 返回一个包含5个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    /**
     * 创建一个包含6个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @return 返回一个包含6个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6);
    }

    /**
     * 创建一个包含7个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @return 返回一个包含7个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7);
    }

    /**
     * 创建一个包含8个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @return 返回一个包含8个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7, K k8, V v8) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8);
    }

    /**
     * 创建一个包含9个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @param k9 键9的Key
     * @param v9 键9的Value
     * @return 返回一个包含9个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9);
    }

    /**
     * 创建一个包含10个键值对的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @param k9 键9的Key
     * @param v9 键9的Value
     * @param k10 键10的Key
     * @param v10 键10的Value
     * @return 返回一个包含10个键值对的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        return mapN(false, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
    }

    /**
     * 创建一个包含10个键值对+可变参数的可修改的Map集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @param k9 键9的Key
     * @param v9 键9的Value
     * @param k10 键10的Key
     * @param v10 键10的Value
     * @param objs 额外的键值对对象
     * @return 返回一个包含10个键值对+可变参数的可修改的Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10, Object... objs) {
        List<Object> objects = listOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
        org.apache.commons.collections4.CollectionUtils.addAll(objects, objs);
        return mapN(false, objects.toArray());
    }

    /**
     * 创建一个空的可修改的LinkedHashMap集合
     *
     * @return 返回一个空的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf() {
        return mapN(true);
    }

    /**
     * 创建一个包含1个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @return 返回一个包含1个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1) {
        return mapN(true, k1, v1);
    }

    /**
     * 创建一个包含2个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @return 返回一个包含2个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2) {
        return mapN(true, k1, v1, k2, v2);
    }

    /**
     * 创建一个包含3个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @return 返回一个包含3个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        return mapN(true, k1, v1, k2, v2, k3, v3);
    }

    /**
     * 创建一个包含4个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @return 返回一个包含4个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4);
    }

    /**
     * 创建一个包含5个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @return 返回一个包含5个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    /**
     * 创建一个包含6个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @return 返回一个包含6个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                               K k6, V v6) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6);
    }

    /**
     * 创建一个包含7个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @return 返回一个包含7个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                               K k6, V v6, K k7, V v7) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7);
    }

    /**
     * 创建一个包含8个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @return 返回一个包含8个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                               K k6, V v6, K k7, V v7, K k8, V v8) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8);
    }

    /**
     * 创建一个包含9个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @param k9 键9的Key
     * @param v9 键9的Value
     * @return 返回一个包含9个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                               K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9);
    }

    /**
     * 创建一个包含10个键值对的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @param k9 键9的Key
     * @param v9 键9的Value
     * @param k10 键10的Key
     * @param v10 键10的Value
     * @return 返回一个包含10个键值对的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                               K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        return mapN(true, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
    }

    /**
     * 创建一个包含10个键值对+可变参数的可修改的LinkedHashMap集合
     *
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @param k7 键7的Key
     * @param v7 键7的Value
     * @param k8 键8的Key
     * @param v8 键8的Value
     * @param k9 键9的Key
     * @param v9 键9的Value
     * @param k10 键10的Key
     * @param v10 键10的Value
     * @param objs 额外的键值对对象
     * @return 返回一个包含10个键值对+可变参数的可修改的LinkedHashMap集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> linkedMapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                               K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10, Object... objs) {
        List<Object> objects = listOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
        org.apache.commons.collections4.CollectionUtils.addAll(objects, objs);
        return mapN(true, objects.toArray());
    }

    /**
     * 创建一个空的可修改的有序Map集合
     * @param comparator 排序比较器
     * @return 返回一个空的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator) {
        return sortMapN(comparator);
    }

    /**
     * 创建一个包含1对键值对的可修改的有序Map集合
     * @param comparator 排序比较器
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @return 返回一个空的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1) {
        return sortMapN(comparator, k1, v1);
    }

    /**
     * 创建一个包含2个键值对的可修改的有序Map集合
     * @param comparator 排序比较器
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @return 返回一个包含2个键值对的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2) {
        return sortMapN(comparator, k1, v1, k2, v2);
    }

    /**
     * 创建一个包含3个键值对的可修改的有序Map集合
     * @param comparator 排序比较器
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @return 返回一个包含3个键值对的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3);
    }

    /**
     * 创建一个包含4个键值对的可修改的有序Map集合
     * @param comparator 排序比较器
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @return 返回一个包含4个键值对的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4);
    }

    /**
     * 创建一个包含5个键值对的可修改的有序Map集合
     * @param comparator 排序比较器
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @return 返回一个包含5个键值对的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    /**
     * 创建一个包含6个键值对的可修改的有序Map集合
     * @param comparator 排序比较器
     * @param k1 键1的Key
     * @param v1 键1的Value
     * @param k2 键2的Key
     * @param v2 键2的Value
     * @param k3 键3的Key
     * @param v3 键3的Value
     * @param k4 键4的Key
     * @param v4 键4的Value
     * @param k5 键5的Key
     * @param v5 键5的Value
     * @param k6 键6的Key
     * @param v6 键6的Value
     * @return 返回一个包含6个键值对的可修改的有序Map集合
     * @param <K> key的类型
     * @param <V> value的类型
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                             K k6, V v6) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6);
    }

    /**
     * 创建排序Map（7个键值对）
     * @param <K> 键类型
     * @param <V> 值类型
     * @param comparator 键比较器
     * @param k1 键1
     * @param v1 值1
     * @param k2 键2
     * @param v2 值2
     * @param k3 键3
     * @param v3 值3
     * @param k4 键4
     * @param v4 值4
     * @param k5 键5
     * @param v5 值5
     * @param k6 键6
     * @param v6 值6
     * @param k7 键7
     * @param v7 值7
     * @return 排序后的Map
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                             K k6, V v6, K k7, V v7) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7);
    }

    /**
     * 创建排序Map（8个键值对）
     * @param <K> 键类型
     * @param <V> 值类型
     * @param comparator 键比较器
     * @param k1 键1
     * @param v1 值1
     * @param k2 键2
     * @param v2 值2
     * @param k3 键3
     * @param v3 值3
     * @param k4 键4
     * @param v4 值4
     * @param k5 键5
     * @param v5 值5
     * @param k6 键6
     * @param v6 值6
     * @param k7 键7
     * @param v7 值7
     * @param k8 键8
     * @param v8 值8
     * @return 排序后的Map
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                             K k6, V v6, K k7, V v7, K k8, V v8) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8);
    }

    /**
     * 创建排序Map（9个键值对）
     * @param <K> 键类型
     * @param <V> 值类型
     * @param comparator 键比较器
     * @param k1 键1
     * @param v1 值1
     * @param k2 键2
     * @param v2 值2
     * @param k3 键3
     * @param v3 值3
     * @param k4 键4
     * @param v4 值4
     * @param k5 键5
     * @param v5 值5
     * @param k6 键6
     * @param v6 值6
     * @param k7 键7
     * @param v7 值7
     * @param k8 键8
     * @param v8 值8
     * @param k9 键9
     * @param v9 值9
     * @return 排序后的Map
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                             K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9);
    }

    /**
     * 创建排序Map（10个键值对）
     * @param <K> 键类型
     * @param <V> 值类型
     * @param comparator 键比较器
     * @param k1 键1
     * @param v1 值1
     * @param k2 键2
     * @param v2 值2
     * @param k3 键3
     * @param v3 值3
     * @param k4 键4
     * @param v4 值4
     * @param k5 键5
     * @param v5 值5
     * @param k6 键6
     * @param v6 值6
     * @param k7 键7
     * @param v7 值7
     * @param k8 键8
     * @param v8 值8
     * @param k9 键9
     * @param v9 值9
     * @param k10 键10
     * @param v10 值10
     * @return 排序后的Map
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                             K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        return sortMapN(comparator, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5,
                k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
    }

    /**
     * 创建排序Map（10个键值对+可变参数）
     * @param <K> 键类型
     * @param <V> 值类型
     * @param comparator 键比较器
     * @param k1 键1
     * @param v1 值1
     * @param k2 键2
     * @param v2 值2
     * @param k3 键3
     * @param v3 值3
     * @param k4 键4
     * @param v4 值4
     * @param k5 键5
     * @param v5 值5
     * @param k6 键6
     * @param v6 值6
     * @param k7 键7
     * @param v7 值7
     * @param k8 键8
     * @param v8 值8
     * @param k9 键9
     * @param v9 值9
     * @param k10 键10
     * @param v10 值10
     * @param objs 额外的键值对对象
     * @return 排序后的Map
     */
    public static <K, V> Map<K, V> sortMapOf(Comparator<? super K> comparator,
                                             K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6,
                                             K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10, Object... objs) {
        List<Object> objects = listOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
        org.apache.commons.collections4.CollectionUtils.addAll(objects, objs);
        return sortMapN(comparator, objects.toArray());
    }

    /**
     * 创建一个可修改的List集合
     *
     * @param elements 待添加的元素
     * @return 返回一个可修改的List集合
     * @param <E> 元素的类型
     */
    @SafeVarargs
    public static <E> List<E> listOf(E... elements) {
        return Lists.newArrayList(elements);
    }

    /**
     * 创建一个可修改的Set集合
     *
     * @param elements 待添加的元素
     * @return 返回一个可修改的Set集合
     * @param <E> 元素的类型
     */
    @SafeVarargs
    public static <E> Set<E> setOf(E... elements) {
        int size = 0;
        if (Objects.nonNull(elements)) {
            size = elements.length;
        }
        Set<E> set = new HashSet<>(size);
        set.addAll(Arrays.asList(elements));
        return set;
    }

    /**
     * 将一个集合类型转换为一个可修改的Set集合
     * @param list 待转换的元素
     * @return 返回一个可修改的Set集合
     * @param <E> 元素的类型
     */
    public static <E> Set<E> setOf(Collection<E> list) {
        return new HashSet<>(list);
    }

    /**
     * 内部方法：创建Map集合
     * @param linked 是否使用LinkedHashMap
     * @param input 输入参数数组
     * @return 创建的Map集合
     * @param <K> 键类型
     * @param <V> 值类型
     */
    static <K, V> Map<K, V> mapN(boolean linked, Object... input) {
        if ((input.length & 1) != 0) {
            throw new InternalError("length is odd");
        }
        Map<K, V> map;
        if (linked) {
            map = new LinkedHashMap<>(input.length / EXPAND_FACTOR);
        } else {
            map = new HashMap<>(input.length / EXPAND_FACTOR);
        }
        fillCollection(map, input);
        return map;
    }

    /**
     * 内部方法：填充Map集合
     * @param map 要填充的Map集合
     * @param input 输入参数数组
     * @param <K> 键类型
     * @param <V> 值类型
     */
    private static <K, V> void fillCollection(Map<K, V> map, Object[] input) {
        for (var i = 0; i < input.length; i += EXPAND_FACTOR) {
            @SuppressWarnings("unchecked")
            K k = Objects.requireNonNull((K) input[i]);
            @SuppressWarnings("unchecked")
            V v = Objects.requireNonNull((V) input[i + 1]);
            map.put(k, v);
        }
    }

    /**
     * 内部方法：创建排序Map集合
     * @param comparator 排序比较器
     * @param input 输入参数数组
     * @return 创建的排序Map集合
     * @param <K> 键类型
     * @param <V> 值类型
     */
    static <K, V> Map<K, V> sortMapN(Comparator<? super K> comparator, Object... input) {
        if ((input.length & 1) != 0) {
            throw new InternalError("length is odd");
        }
        SortedMap<K, V> map = new TreeMap<>(comparator);
        fillCollection(map, input);
        return map;
    }
}
