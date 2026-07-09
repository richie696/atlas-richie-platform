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
package com.richie.component.concurrency.registry;

import com.richie.component.concurrency.algorithm.CircuitBreaker;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Per-key {@link CircuitBreaker} 缓存注册中心（README.md §设计选择 / 关键设计决策 R6：Registry 归属 concurrency）。
 * <p>
 * 拦截器层（{@code CircuitBreakerInterceptor}）通过此 Registry 按 {@code clientKey} 获取独立熔断器，
 * 实现"每客户端独立熔断统计"语义。{@link CircuitBreaker} 单例 bean 不接收 key 参数，本 Registry
 * 提供了按 key 创建并缓存的机制。
 *
 * <h2>与 {@link RateLimiterRegistry} 的差异</h2>
 * <ul>
 *   <li>CircuitBreaker 状态机本身是线程安全的（内部维护失败计数 / 状态），但 <em>Registry 视角</em>
 *       下不同 key 必须独立——同一进程内"全局熔断"语义对多租户场景是反模式</li>
 *   <li>调用 {@link #remove(String)} 后调用方负责 {@code cb.reset()} 或重新创建</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public interface CircuitBreakerRegistry {

    /**
     * 按 key 获取或创建 {@link CircuitBreaker}。同一 key 多次调用返回同一实例（缓存语义）。
     *
     * @param key     熔断键（通常是 {@code clientKey} / IP / API 等），非 null
     * @param factory 首次创建该 key 的熔断器时调用，非 null
     * @return 缓存中或新创建的熔断器实例
     * @throws NullPointerException 如果 {@code key} 或 {@code factory} 为 null
     */
    CircuitBreaker getOrCreate(String key, Function<String, CircuitBreaker> factory);

    /**
     * 查找 key 对应的熔断器；不存在返回 {@link Optional#empty()}。
     */
    Optional<CircuitBreaker> find(String key);

    /**
     * 移除 key 对应的熔断器，调用方负责重置或关闭返回的实例。
     */
    Optional<CircuitBreaker> remove(String key);

    /**
     * 当前缓存的熔断器实例数。
     */
    int size();

    /**
     * 当前所有 key 的快照（不可修改）。
     */
    Set<String> keys();

    /**
     * 清空所有缓存（不重置内部熔断器状态）。
     * <p>主要用于测试与运维重置；生产代码慎用。
     */
    void clear();
}