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

import com.richie.component.cache.operations.BoundedListCapacityLimits;
import com.richie.component.cache.operations.BoundedQueue;

/**
 * 有界队列（Bounded FIFO Queue）管理接口。
 * <p>
 * 通过 {@link com.richie.component.cache.GlobalCache#queue()} 获取实例。
 * 创建后容量默认不可变；扩容仅能通过 {@link BoundedQueue#grow()}（单次 ×2，封顶
 * {@link BoundedListCapacityLimits#BOUNDED_MAX_LEN_CEILING}）。
 *
 * @author richie696
 * @since 2026-06-04
 */
public interface BoundedQueueOps {

    /**
     * 创建有界队列。同一 key 已存在时抛异常。
     *
     * @param key    队列 key
     * @param maxLen 队列最大长度，须在 [{@link BoundedListCapacityLimits#MIN_MAX_LEN},
     *               {@link BoundedListCapacityLimits#BOUNDED_MAX_LEN_CEILING}] 内
     */
    <T> BoundedQueue<T> create(String key, long maxLen, Class<T> clazz);

    <T> BoundedQueue<T> get(String key, Class<T> clazz);

    /**
     * 获取或创建。已存在时校验 {@code maxLen} 与 meta 一致，否则抛异常。
     */
    <T> BoundedQueue<T> getOrCreate(String key, long maxLen, Class<T> clazz);

    boolean exists(String key);

    boolean destroy(String key);

    boolean expire(String key, long timeout);

    /**
     * 将指定队列容量翻倍（平台托管，不可缩小）。
     *
     * @return 扩容成功 {@code true}，已达封顶 {@code false}
     * @throws IllegalArgumentException 队列不存在
     */
    boolean grow(String key);
}
