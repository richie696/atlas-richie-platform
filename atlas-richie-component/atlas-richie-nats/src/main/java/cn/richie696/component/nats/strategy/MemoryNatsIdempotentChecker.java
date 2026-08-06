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
package cn.richie696.component.nats.strategy;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的 NATS 消息幂等去重实现
 *
 * <p>使用 {@link ConcurrentHashMap} + 时间戳实现本地去重，适用于单实例部署。
 * 多实例部署请使用 {@link RedisNatsIdempotentChecker}。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class MemoryNatsIdempotentChecker implements NatsIdempotentChecker {

    /**
     * 消息 ID → 首次见到时间戳（毫秒），仅用于 TTL 到期回收。
     * {@link ConcurrentHashMap} 保证 {@code putIfAbsent} 在并发消费者下也是原子的“是否首次”判断。
     */
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    /**
     * 判断 {@code messageId} 是否首次出现，并在同时清理过期记录后以原子方式登记此次出现时间。
     *
     * @param messageId 消息唯一标识
     * @param ttlMillis 去重 TTL（毫秒），用于回收过期条目
     * @return {@code true} 表示首次处理；{@code false} 表示重复消息
     */
    @Override
    public boolean isFirstTime(String messageId, long ttlMillis) {
        long now = System.currentTimeMillis();
        // 惰性回收：每次判定时顺手清理过期条目，避免无限增长；单实例且 TTL 通常较短，开销可控。
        seen.entrySet().removeIf(entry -> now - entry.getValue() > ttlMillis);
        // 原子“先占位再返回是否抢到”：返回 null 即抢到名额（即首次出现）。
        return seen.putIfAbsent(messageId, now) == null;
    }

    /**
     * 清除 {@code messageId} 的去重记录，使后续重试可以再次通过首次检查。
     *
     * @param messageId 消息唯一标识
     */
    @Override
    public void clear(String messageId) {
        seen.remove(messageId);
    }
}
