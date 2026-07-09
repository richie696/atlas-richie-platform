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
package com.richie.component.cache.ops;

import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * KV 缓存 + 计数器操作接口。
 * <p>对应底层 String 数据结构，提供基本类型/对象的存取、原子计数、批量操作及防缓存击穿能力。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface ValueOps {

    // ─────────────────────── 读 ───────────────────────

    <T> T get(String key, Class<T> clazz);

    <T> T get(String key, TypeReference<T> reference);

    <T> Map<String, T> getMap(Collection<String> keys, TypeReference<T> reference);

    <T> List<T> getList(Collection<String> keys, TypeReference<T> reference);

    // ─────────────────────── 写 —— String ───────────────────────

    void set(String key, String value);

    boolean setIfAbsent(String key, String value);

    void set(String key, String value, long timeoutMillis);

    boolean setIfAbsent(String key, String value, long timeoutMillis);

    // ─────────────────────── 写 —— int ───────────────────────

    void set(String key, int value);

    boolean setIfAbsent(String key, int value);

    void set(String key, int value, long timeoutMillis);

    boolean setIfAbsent(String key, int value, long timeoutMillis);

    // ─────────────────────── 写 —— long ───────────────────────

    void set(String key, long value);

    boolean setIfAbsent(String key, long value);

    void set(String key, long value, long timeoutMillis);

    boolean setIfAbsent(String key, long value, long timeoutMillis);

    // ─────────────────────── 写 —— float ───────────────────────

    void set(String key, float value);

    boolean setIfAbsent(String key, float value);

    void set(String key, float value, long timeoutMillis);

    boolean setIfAbsent(String key, float value, long timeoutMillis);

    // ─────────────────────── 写 —— double ───────────────────────

    void set(String key, double value);

    boolean setIfAbsent(String key, double value);

    void set(String key, double value, long timeoutMillis);

    boolean setIfAbsent(String key, double value, long timeoutMillis);

    // ─────────────────────── 写 —— boolean ───────────────────────

    void set(String key, boolean value);

    boolean setIfAbsent(String key, boolean value);

    void set(String key, boolean value, long timeoutMillis);

    boolean setIfAbsent(String key, boolean value, long timeoutMillis);

    // ─────────────────────── 原子计数器 ───────────────────────

    long increment(String key);

    long increment(String key, long delta);

    long increment(String key, long delta, long timeoutMillis);

    double increment(String key, double delta, long timeoutMillis);

    long decrement(String key);

    long decrement(String key, long delta);

    long decrement(String key, long delta, long timeoutMillis);

    // ─────────────────────── 批量 ───────────────────────

    void batchSet(Map<String, ?> map);

    void batchSet(Map<String, ?> map, long timeoutMillis);

    void batchSetIfAbsent(Map<String, ?> batch);

    void batchSetIfAbsent(Map<String, ?> batch, long timeoutMillis);

    // ─────────────────────── 防缓存击穿 ───────────────────────

    String getWithLock(String key, long timeoutMillis, Supplier<String> dbLoader);
}
