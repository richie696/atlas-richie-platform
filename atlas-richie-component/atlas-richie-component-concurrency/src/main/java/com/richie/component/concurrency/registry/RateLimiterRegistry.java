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
package com.richie.component.concurrency.registry;

import com.richie.component.concurrency.algorithm.RateLimiter;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Per-key {@link RateLimiter} 缓存注册中心（README.md §设计选择 / 关键设计决策 R6：Registry 归属 concurrency）。
 * <p>
 * 拦截器层（{@code RateLimitInterceptor}）通过此 Registry 按 {@code clientKey} 获取独立桶，
 * 实现"每客户端独立限流"语义。{@link RateLimiter} 单例 bean 不接收 key 参数，本 Registry
 * 是解药：调用方在 Registry 内按 key 创建并缓存独立实例。
 *
 * <h2>并发安全</h2>
 * <p>实现必须线程安全（拦截器在多线程并发下访问）。默认实现使用 {@code ConcurrentHashMap} + 
 * {@code computeIfAbsent} 保证同一 key 仅创建一次。
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li>Registry 自身不需要关闭（仅持有 {@link RateLimiter} 引用）</li>
 *   <li>调用方如需释放某 key 的限流器，调 {@link #remove(String)}，自行关闭返回的实例</li>
 *   <li>Registry 不会自动 close 其内部的 {@link RateLimiter}（避免未预期的副作用）</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public interface RateLimiterRegistry {

    /**
     * 按 key 获取或创建 {@link RateLimiter}。同一 key 多次调用返回同一实例（缓存语义）。
     *
     * @param key     限流键（通常是 {@code clientKey} / IP / API 等），非 null
     * @param factory 首次创建该 key 的限流器时调用，非 null；返回的 {@link RateLimiter} 实例
     *                由 Registry 持有，不要在外部关闭
     * @return 缓存中或新创建的限流器实例
     * @throws NullPointerException 如果 {@code key} 或 {@code factory} 为 null
     */
    RateLimiter getOrCreate(String key, Function<String, RateLimiter> factory);

    /**
     * 查找 key 对应的限流器；不存在返回 {@link Optional#empty()}。
     */
    Optional<RateLimiter> find(String key);

    /**
     * 移除 key 对应的限流器，调用方负责关闭返回的实例。
     *
     * @return 被移除的限流器（如有）；不存在返回 {@link Optional#empty()}
     */
    Optional<RateLimiter> remove(String key);

    /**
     * 当前缓存的限流器实例数。
     */
    int size();

    /**
     * 当前所有 key 的快照（按当前顺序，不可修改）。
     */
    Set<String> keys();

    /**
     * 清空所有缓存的限流器（不自动关闭内部实例）。
     * <p>主要用于测试与运维重置；生产代码慎用。
     */
    void clear();
}