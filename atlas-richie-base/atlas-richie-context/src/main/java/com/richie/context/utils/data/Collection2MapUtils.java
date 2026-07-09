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
package com.richie.context.utils.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.*;
import java.util.function.Function;

/**
 * collection转换为map映射
 * 解决：key不能重复，报异常和value为null报异常
 * 泛型
 * E：集合元素
 * RK：return key
 * RV：return value
 *
 * @author yuy
 * @version 1.0
 * @since 2023-09-27 14:08:37
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Collection2MapUtils {

    /**
     * 集合映射为一对一
     * @param <E> 集合元素类型
     * @param <RK> 返回键类型
     * @param c    集合
     * @param keyFuc key函数
     * @return 映射后的Map
     */
    public static <E, RK> Map<RK, E> collection2Map(Collection<E> c, Function<E, RK> keyFuc) {
        return Optional.ofNullable(c).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .collect(HashMap::new,
                        (m, v) -> Optional.ofNullable(keyFuc.apply(v)).ifPresent(k -> m.put(k, v)),
                        HashMap::putAll);
    }

    /**
     * 集合映射为一对一
     * @param <E> 集合元素类型
     * @param <RK> 返回键类型
     * @param <RV> 返回值类型
     * @param c    集合
     * @param keyFuc key函数
     * @param valFuc value函数
     * @return 映射后的Map
     */
    public static <E, RK, RV> Map<RK, RV> collection2Map(Collection<E> c, Function<E, RK> keyFuc, Function<E, RV> valFuc) {
        return Optional.ofNullable(c).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .collect(HashMap::new,
                        (m, v) -> Optional.ofNullable(keyFuc.apply(v)).ifPresent(k -> m.put(k, valFuc.apply(v))),
                        HashMap::putAll);
    }

    /**
     * 集合映射为一对多
     * @param <E> 集合元素类型
     * @param <RK> 返回键类型
     * @param c    集合
     * @param keyFuc key函数
     * @return 映射后的Map
     */
    public static <E, RK> Map<RK, Collection<E>> collection2MapCollection(Collection<E> c, Function<E, RK> keyFuc) {
        return Optional.ofNullable(c).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .collect(HashMap::new,
                        (m, v) -> Optional.ofNullable(keyFuc.apply(v)).ifPresent(k -> m.computeIfAbsent(k, x -> new ArrayList<>()).add(v)),
                        HashMap::putAll);
    }

    /**
     * 集合映射为一对多
     * @param <E> 集合元素类型
     * @param <RK> 返回键类型
     * @param <RV> 返回值类型
     * @param c    集合
     * @param keyFuc key函数
     * @param valFuc value函数
     * @return 映射后的Map
     */
    public static <E, RK,RV> Map<RK, Collection<RV>> collection2MapCollection(Collection<E> c, Function<E, RK> keyFuc, Function<E, RV> valFuc) {
        return Optional.ofNullable(c).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .collect(HashMap::new,
                        (m, v) -> Optional.ofNullable(keyFuc.apply(v)).ifPresent(k -> m.computeIfAbsent(k, x -> new ArrayList<>()).add(valFuc.apply(v))),
                        HashMap::putAll);
    }


}
