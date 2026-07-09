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
package com.richie.component.web.core.business;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等 key 缓存（同 NonceCache 设计思想，单实例部署；多实例需换 Redis SETEX）。
 * <p>
 * key = 幂等指纹（业务方传）；value = 首次插入时间戳（ms）。
 *
 * <h2>适用</h2>
 * <ul>
 *   <li>单实例部署 OK</li>
 *   <li>多实例需换 Redis SETEX 或 BloomFilter</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public class IdempotencyCache {

    private final long ttlMillis;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public IdempotencyCache(long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be > 0");
        }
        this.ttlMillis = ttlSeconds * 1000L;
    }

    /**
     * 若 idempotencyKey 不存在（且未过期）则放入并返回 {@code true}；否则返回 {@code false}。
     */
    public boolean putIfAbsent(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        long now = System.currentTimeMillis();
        Long previous = cache.putIfAbsent(idempotencyKey, now);
        if (previous == null) {
            return true;
        }
        if (now - previous > ttlMillis) {
            cache.put(idempotencyKey, now);
            return true;
        }
        return false;
    }

    public int size() {
        long now = System.currentTimeMillis();
        int live = 0;
        for (var entry : cache.entrySet()) {
            if (now - entry.getValue() <= ttlMillis) {
                live++;
            } else {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
        return live;
    }
}