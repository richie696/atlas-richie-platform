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
package com.richie.component.cache.ops;

import tools.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 有序集合/排行榜操作接口。
 * <p>对应底层 ZSet 数据结构，支持分数排序、排名查询、范围扫描及弹出操作。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface RankingOps {

    void set(String key, Object value, double score);

    void setAll(String key, TreeSet<?> orderSet);

    void batchSet(Map<String, TreeSet<?>> map);

    long size(String key);

    void remove(String key, Object... values);

    void removeByRank(String key, long start, long end);

    void removeByScore(String key, double min, double max);

    double incrementScore(String key, Object value, double delta);

    <T> T popMin(String key, TypeReference<T> reference);

    <T> Set<T> popMin(String key, long count, TypeReference<T> reference);

    <T> Set<T> range(String key, long start, long end, TypeReference<T> reference);

    <T> Set<T> rangeByScore(String key, double minScore, double maxScore, TypeReference<T> reference);

    long reverseRank(String key, Object value);
}
