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

import com.richie.component.cache.operations.BoundedListCapacityLimits;
import com.richie.component.cache.operations.BoundedStack;

/**
 * 有界栈（Bounded LIFO Stack）管理接口。
 * <p>
 * 通过 {@link com.richie.component.cache.GlobalCache#stack()} 获取实例。
 * 容量治理规则同 {@link BoundedQueueOps}。
 *
 * @author richie696
 * @since 2026-06-04
 */
public interface BoundedStackOps {

    <T> BoundedStack<T> create(String key, long maxLen, Class<T> clazz);

    <T> BoundedStack<T> get(String key, Class<T> clazz);

    <T> BoundedStack<T> getOrCreate(String key, long maxLen, Class<T> clazz);

    boolean exists(String key);

    boolean destroy(String key);

    boolean expire(String key, long timeout);

    /**
     * 将指定栈容量翻倍（平台托管，不可缩小）。
     *
     * @return 扩容成功 {@code true}，已达封顶 {@code false}
     * @throws IllegalArgumentException 栈不存在
     */
    boolean grow(String key);
}
