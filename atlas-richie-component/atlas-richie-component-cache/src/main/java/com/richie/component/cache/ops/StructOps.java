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

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 结构化对象缓存操作接口。
 * <p>对应底层 Hash 数据结构，将 JavaBean 作为整体存取，适用于对象的全体读写、刷新及防缓存击穿。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface StructOps {

    <T> T get(String key, Class<T> clazz);

    <T> T get(String key, TypeReference<T> reference);

    void set(String key, Object value);

    void set(String key, Object value, long timeoutMillis);

    <T> T refresh(String key, UnaryOperator<T> func);

    <T> T getWithLock(String key, Class<T> clazz, long timeoutMillis, Supplier<T> dbLoader);

    <T> T getWithLock(String key, TypeReference<T> reference, long timeoutMillis, Supplier<T> dbLoader);
}
