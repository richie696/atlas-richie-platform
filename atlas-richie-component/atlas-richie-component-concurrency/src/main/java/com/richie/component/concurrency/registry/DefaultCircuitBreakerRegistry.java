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
package com.richie.component.concurrency.registry;

import com.richie.component.concurrency.algorithm.CircuitBreaker;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * {@link CircuitBreakerRegistry} 默认实现（基于 {@link ConcurrentHashMap}）。
 * <p>
 * 设计要点与 {@link DefaultRateLimiterRegistry} 一致：
 * <ul>
 *   <li>{@link ConcurrentHashMap#computeIfAbsent(Object, Function)} 保证同一 key 的 factory
 *       在并发下仅调用一次</li>
 *   <li>{@link #remove(String)} 仅从缓存中删除，<strong>不</strong>自动重置内部熔断器状态——
 *       调用方拿到返回值后自行决定生命周期</li>
 *   <li>无容量上限：调用方应自行加限</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public final class DefaultCircuitBreakerRegistry implements CircuitBreakerRegistry {

    private final ConcurrentMap<String, CircuitBreaker> map = new ConcurrentHashMap<>();

    public DefaultCircuitBreakerRegistry() {
    }

    @Override
    public CircuitBreaker getOrCreate(String key, Function<String, CircuitBreaker> factory) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        return map.computeIfAbsent(key, factory);
    }

    @Override
    public Optional<CircuitBreaker> find(String key) {
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public Optional<CircuitBreaker> remove(String key) {
        CircuitBreaker removed = map.remove(key);
        return Optional.ofNullable(removed);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Set<String> keys() {
        return Set.copyOf(map.keySet());
    }

    @Override
    public void clear() {
        map.clear();
    }
}