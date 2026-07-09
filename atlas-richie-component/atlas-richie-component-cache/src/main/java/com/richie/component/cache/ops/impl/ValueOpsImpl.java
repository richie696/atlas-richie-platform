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
package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.StringFunction;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.ops.ValueOps;
import tools.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class ValueOpsImpl implements ValueOps {

    private static final KeyTypeEnum KT = KeyTypeEnum.STRING;

    private final StringFunction fn;
    private final L2SyncHelper l2;

    // ─────────────────────── 读 ───────────────────────

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return l2.get(KT, key, () -> fn.getFromString(key, clazz));
    }

    @Override
    public <T> T get(String key, TypeReference<T> reference) {
        return l2.get(KT, key, () -> fn.getFromString(key, reference));
    }

    @Override
    public <T> Map<String, T> getMap(Collection<String> keys, TypeReference<T> reference) {
        return fn.getValueMap(List.copyOf(keys), reference);
    }

    @Override
    public <T> List<T> getList(Collection<String> keys, TypeReference<T> reference) {
        return fn.getObjects(List.copyOf(keys), reference);
    }

    // ─────────────────────── 写 —— String ───────────────────────

    @Override
    public void set(String key, String value) {
        fn.addValue(key, value);
        l2.registerType(key, String.class);
        l2.put(KT, key, value);
    }

    @Override
    public boolean setIfAbsent(String key, String value) {
        boolean result = fn.addValueIfAbsent(key, value);
        if (result) {
            l2.registerType(key, String.class);
            l2.put(KT, key, value);
        }
        return result;
    }

    @Override
    public void set(String key, String value, long timeoutMillis) {
        fn.addValue(key, value, timeoutMillis);
        l2.registerType(key, String.class);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public boolean setIfAbsent(String key, String value, long timeoutMillis) {
        boolean result = fn.addValueIfAbsent(key, value, timeoutMillis);
        if (result) {
            l2.registerType(key, String.class);
            l2.put(KT, key, value, timeoutMillis);
        }
        return result;
    }

    // ─────────────────────── 写 —— int ───────────────────────

    @Override
    public void set(String key, int value) {
        fn.addValue(key, value);
        l2.registerType(key, Integer.class);
        l2.put(KT, key, value);
    }

    @Override
    public boolean setIfAbsent(String key, int value) {
        boolean result = fn.addValueIfAbsent(key, value);
        if (result) {
            l2.registerType(key, Integer.class);
            l2.put(KT, key, value);
        }
        return result;
    }

    @Override
    public void set(String key, int value, long timeoutMillis) {
        fn.addValue(key, value, timeoutMillis);
        l2.registerType(key, Integer.class);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public boolean setIfAbsent(String key, int value, long timeoutMillis) {
        boolean result = fn.addValueIfAbsent(key, value, timeoutMillis);
        if (result) {
            l2.registerType(key, Integer.class);
            l2.put(KT, key, value, timeoutMillis);
        }
        return result;
    }

    // ─────────────────────── 写 —— long ───────────────────────

    @Override
    public void set(String key, long value) {
        fn.addValue(key, value);
        l2.registerType(key, Long.class);
        l2.put(KT, key, value);
    }

    @Override
    public boolean setIfAbsent(String key, long value) {
        boolean result = fn.addValueIfAbsent(key, value);
        if (result) {
            l2.registerType(key, Long.class);
            l2.put(KT, key, value);
        }
        return result;
    }

    @Override
    public void set(String key, long value, long timeoutMillis) {
        fn.addValue(key, value, timeoutMillis);
        l2.registerType(key, Long.class);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public boolean setIfAbsent(String key, long value, long timeoutMillis) {
        boolean result = fn.addValueIfAbsent(key, value, timeoutMillis);
        if (result) {
            l2.registerType(key, Long.class);
            l2.put(KT, key, value, timeoutMillis);
        }
        return result;
    }

    // ─────────────────────── 写 —— float ───────────────────────

    @Override
    public void set(String key, float value) {
        fn.addValue(key, value);
        l2.registerType(key, Float.class);
        l2.put(KT, key, value);
    }

    @Override
    public boolean setIfAbsent(String key, float value) {
        boolean result = fn.addValueIfAbsent(key, value);
        if (result) {
            l2.registerType(key, Float.class);
            l2.put(KT, key, value);
        }
        return result;
    }

    @Override
    public void set(String key, float value, long timeoutMillis) {
        fn.addValue(key, value, timeoutMillis);
        l2.registerType(key, Float.class);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public boolean setIfAbsent(String key, float value, long timeoutMillis) {
        boolean result = fn.addValueIfAbsent(key, value, timeoutMillis);
        if (result) {
            l2.registerType(key, Float.class);
            l2.put(KT, key, value, timeoutMillis);
        }
        return result;
    }

    // ─────────────────────── 写 —— double ───────────────────────

    @Override
    public void set(String key, double value) {
        fn.addValue(key, value);
        l2.registerType(key, Double.class);
        l2.put(KT, key, value);
    }

    @Override
    public boolean setIfAbsent(String key, double value) {
        boolean result = fn.addValueIfAbsent(key, value);
        if (result) {
            l2.registerType(key, Double.class);
            l2.put(KT, key, value);
        }
        return result;
    }

    @Override
    public void set(String key, double value, long timeoutMillis) {
        fn.addValue(key, value, timeoutMillis);
        l2.registerType(key, Double.class);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public boolean setIfAbsent(String key, double value, long timeoutMillis) {
        boolean result = fn.addValueIfAbsent(key, value, timeoutMillis);
        if (result) {
            l2.registerType(key, Double.class);
            l2.put(KT, key, value, timeoutMillis);
        }
        return result;
    }

    // ─────────────────────── 写 —— boolean ───────────────────────

    @Override
    public void set(String key, boolean value) {
        fn.addValue(key, value);
        l2.registerType(key, Boolean.class);
        l2.put(KT, key, value);
    }

    @Override
    public boolean setIfAbsent(String key, boolean value) {
        boolean result = fn.addValueIfAbsent(key, value);
        if (result) {
            l2.registerType(key, Boolean.class);
            l2.put(KT, key, value);
        }
        return result;
    }

    @Override
    public void set(String key, boolean value, long timeoutMillis) {
        fn.addValue(key, value, timeoutMillis);
        l2.registerType(key, Boolean.class);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public boolean setIfAbsent(String key, boolean value, long timeoutMillis) {
        boolean result = fn.addValueIfAbsent(key, value, timeoutMillis);
        if (result) {
            l2.registerType(key, Boolean.class);
            l2.put(KT, key, value, timeoutMillis);
        }
        return result;
    }

    // ─────────────────────── 原子计数器 ───────────────────────

    @Override
    public long increment(String key) {
        long result = fn.increment(key, null);
        l2.registerType(key, Long.class);
        l2.put(KT, key, result);
        return result;
    }

    @Override
    public long increment(String key, long delta) {
        long result = fn.increment(key, delta, null);
        l2.registerType(key, Long.class);
        l2.put(KT, key, result);
        return result;
    }

    @Override
    public long increment(String key, long delta, long timeoutMillis) {
        long result = fn.increment(key, delta, timeoutMillis);
        l2.registerType(key, Long.class);
        l2.put(KT, key, result, timeoutMillis);
        return result;
    }

    @Override
    public double increment(String key, double delta, long timeoutMillis) {
        double result = fn.increment(key, delta, timeoutMillis);
        l2.registerType(key, Double.class);
        l2.put(KT, key, result, timeoutMillis);
        return result;
    }

    @Override
    public long decrement(String key) {
        long result = fn.decrement(key, null);
        l2.registerType(key, Long.class);
        l2.put(KT, key, result);
        return result;
    }

    @Override
    public long decrement(String key, long delta) {
        long result = fn.decrement(key, delta, null);
        l2.registerType(key, Long.class);
        l2.put(KT, key, result);
        return result;
    }

    @Override
    public long decrement(String key, long delta, long timeoutMillis) {
        long result = fn.decrement(key, delta, timeoutMillis);
        l2.registerType(key, Long.class);
        l2.put(KT, key, result, timeoutMillis);
        return result;
    }

    // ─────────────────────── 批量 ───────────────────────

    @Override
    public void batchSet(Map<String, ?> map) {
        fn.batchAddToString(map);
        map.forEach((k, v) -> {
            l2.registerType(k, v.getClass());
            l2.put(KT, k, v);
        });
    }

    @Override
    public void batchSet(Map<String, ?> map, long timeoutMillis) {
        fn.batchAddToString(map, timeoutMillis);
        map.forEach((k, v) -> {
            l2.registerType(k, v.getClass());
            l2.put(KT, k, v, timeoutMillis);
        });
    }

    @Override
    public void batchSetIfAbsent(Map<String, ?> batch) {
        fn.batchUpdateIfAbsent(batch, null);
        batch.forEach((k, v) -> {
            l2.registerType(k, v.getClass());
            l2.put(KT, k, v);
        });
    }

    @Override
    public void batchSetIfAbsent(Map<String, ?> batch, long timeoutMillis) {
        fn.batchUpdateIfAbsent(batch, timeoutMillis);
        batch.forEach((k, v) -> {
            l2.registerType(k, v.getClass());
            l2.put(KT, k, v, timeoutMillis);
        });
    }

    // ─────────────────────── 防缓存击穿 ───────────────────────

    @Override
    public String getWithLock(String key, long timeoutMillis, Supplier<String> dbLoader) {
        return l2.getWithLock(KT, key,
                () -> fn.getFromStringWithLock(key, dbLoader, timeoutMillis));
    }
}
