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
package com.richie.component.cache.ops;

import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Hash 字段级存取操作接口。
 * <p>对应底层 Hash 数据结构，以 field 为粒度进行读写、批量操作及元信息查询。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface FieldOps {

    // ─────────── 单 field ───────────

    void set(String key, String field, Object value);

    <T> T get(String key, String field, Class<T> clazz);

    <T> T get(String key, String field, TypeReference<T> reference);

    boolean exists(String key, String field);

    // ─────────── 多 field ───────────

    void setAll(String key, Map<String, ?> map, long timeoutMillis);

    <T> Map<String, T> getAll(String key, Class<T> clazz);

    <T> List<T> get(String key, Collection<String> fields, TypeReference<T> reference);

    // ─────────── 元信息 ───────────

    Set<String> getFields(String key);

    long size(String key);

    void remove(String key, String... fields);

    // ─────────── 批量 ───────────

    void batchSet(Map<String, Map<String, ?>> map);

    // ─────────── 防击穿 ───────────

    <T> T getWithLock(String key, String field, Class<T> clazz, long timeoutMillis, Supplier<T> dbLoader);

    <T> T getWithLock(String key, String field, TypeReference<T> reference, long timeoutMillis, Supplier<T> dbLoader);
}
