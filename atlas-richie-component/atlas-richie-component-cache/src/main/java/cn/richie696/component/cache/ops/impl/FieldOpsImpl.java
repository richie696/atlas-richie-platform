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
package cn.richie696.component.cache.ops.impl;

import cn.richie696.component.cache.enums.KeyTypeEnum;
import cn.richie696.component.cache.function.HashFunction;
import cn.richie696.component.cache.ops.FieldOps;
import cn.richie696.component.cache.ops.L2SyncHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class FieldOpsImpl implements FieldOps {

    private static final KeyTypeEnum KT = KeyTypeEnum.HASH;

    private final HashFunction fn;
    private final L2SyncHelper l2;

    // ─────────── 单 field ───────────

    @Override
    public void set(String key, String field, Object value) {
        fn.addHash(key, field, value);
        invalidateCachedHashValue(key, field);
    }

    @Override
    public <T> T get(String key, String field, Class<T> clazz) {
        return l2.getWithLock(KT, key + ":" + field,
                () -> fn.getFromHash(key, field, clazz));
    }

    @Override
    public <T> T get(String key, String field, TypeReference<T> reference) {
        return l2.getWithLock(KT, key + ":" + field,
                () -> fn.getFromHash(key, field, reference));
    }

    @Override
    public boolean exists(String key, String field) {
        return fn.existsInHash(key, field);
    }

    // ─────────── 原子计数器 ───────────

    @Override
    public long increment(String key, String field) {
        return increment(key, field, 1L);
    }

    @Override
    public long increment(String key, String field, long delta) {
        long result = fn.incrementHash(key, field, delta);
        invalidateCachedHashValue(key, field);
        return result;
    }

    @Override
    public double increment(String key, String field, double delta) {
        double result = fn.incrementHash(key, field, delta);
        invalidateCachedHashValue(key, field);
        return result;
    }

    @Override
    public long decrement(String key, String field) {
        return decrement(key, field, 1L);
    }

    @Override
    public long decrement(String key, String field, long delta) {
        return increment(key, field, -delta);
    }

    // ─────────── 多 field ───────────

    @Override
    public void setAll(String key, Map<String, ?> map, long timeoutMillis) {
        fn.addHash(key, map);
        l2.registerType(key, Map.class);
        l2.put(KT, key, map, timeoutMillis);
    }

    @Override
    public <T> Map<String, T> getAll(String key, Class<T> clazz) {
        return l2.get(KT, key, () -> fn.getAllMapFromHash(key, clazz));
    }

    @Override
    public <T> List<T> get(String key, Collection<String> fields, TypeReference<T> reference) {
        return fn.getFromHash(key, List.copyOf(fields), reference);
    }

    @Override
    public <T> Map<String, T> get(String key, Collection<String> fields, Class<T> clazz) {
        Set<String> requested = Set.copyOf(fields);
        return l2.get(KeyTypeEnum.HASH, key,
                () -> fn.getFromHash(key, List.copyOf(requested), clazz))
                .entrySet().stream()
                .filter(entry -> requested.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    // ─────────── 元信息 ───────────

    @Override
    public Set<String> getFields(String key) {
        return fn.getHashKeyList(key);
    }

    @Override
    public long size(String key) {
        return fn.getHashSize(key);
    }

    @Override
    public void remove(String key, String... fields) {
        fn.removeHashItem(key, fields);
        l2.remove(key);
        l2.removeAll(Arrays.stream(fields).map(field -> key + ":" + field).toList());
    }

    // ─────────── 批量 ───────────

    @Override
    public void batchSet(Map<String, Map<String, ?>> map) {
        fn.batchAddToHash(map);
        map.keySet().forEach(l2::remove);
    }

    // ─────────── 防击穿 ───────────

    @Override
    public <T> T getWithLock(String key, String field, Class<T> clazz, long timeoutMillis, Supplier<T> dbLoader) {
        String cacheKey = key + ":" + field;
        return l2.getWithLock(KT, cacheKey,
                () -> fn.getFromHashWithLock(key, field, clazz, dbLoader, timeoutMillis));
    }

    @Override
    public <T> T getWithLock(String key, String field, TypeReference<T> reference, long timeoutMillis, Supplier<T> dbLoader) {
        String cacheKey = key + ":" + field;
        return l2.getWithLock(KT, cacheKey,
                () -> fn.getFromHashWithLock(key, field, reference, dbLoader, timeoutMillis));
    }

    @Override
    public <T> Map<String, T> getWithLock(
            String key,
            Collection<String> fields,
            Class<T> clazz,
            long timeoutMillis,
            Supplier<Map<String, T>> dbLoader
    ) {
        Set<String> requested = Set.copyOf(fields);
        return l2.getWithLock(KeyTypeEnum.HASH, key,
                () -> fn.getFromHashWithLock(key, List.copyOf(requested), clazz, dbLoader, timeoutMillis))
                .entrySet().stream()
                .filter(entry -> requested.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private void invalidateCachedHashValue(String key, String field) {
        // field 读和整张 Hash 读使用不同 L2 key，二者都必须失效。
        l2.remove(key + ":" + field);
        l2.remove(key);
    }
}
