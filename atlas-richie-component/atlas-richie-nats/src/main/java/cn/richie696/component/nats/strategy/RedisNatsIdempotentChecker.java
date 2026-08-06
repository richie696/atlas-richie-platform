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

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.nats.NatsConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis 的 NATS 消息幂等去重实现
 *
 * <p>使用 {@link GlobalCache} 的 SET NX 原子操作实现分布式去重，适用于多实例部署。
 * Key 格式：{@code nats:idempotent:{messageId}}，TTL 由配置决定。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
/**
 * 基于 Redis 的 NATS 消息幂等去重实现
 *
 * <p>使用 {@link GlobalCache} 的 SET NX（{@code setIfAbsent}）原子操作实现分布式去重，适用于多实例部署。
 * Key 格式：{@code nats:idempotent:{messageId}}，TTL 由配置决定。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class RedisNatsIdempotentChecker implements NatsIdempotentChecker {

    /**
     * 判断 {@code messageId} 是否首次出现，依赖 Redis 原生 {@code SET key value NX PX ttl} 原子语义。
     *
     * @param messageId 消息唯一标识
     * @param ttlMillis 去重 TTL（毫秒）
     * @return {@code true} 表示首次处理；{@code false} 表示重复消息
     */
    @Override
    public boolean isFirstTime(String messageId, long ttlMillis) {
        String key = NatsConstants.IDEMPOTENT_KEY_PREFIX + messageId;
        // SET NX PX 一步完成“占位 + 设过期”，天然避免 check-then-set 竞态。
        boolean success = GlobalCache.value().setIfAbsent(key, "1", ttlMillis);
        if (!success) {
            log.debug("Duplicate message detected, messageId={}", messageId);
        }
        return success;
    }

    /**
     * 清除 {@code messageId} 的去重记录，允许下次投递重新参与判定（业务异常时由装饰器回调用）。
     *
     * @param messageId 消息唯一标识
     */
    @Override
    public void clear(String messageId) {
        String key = NatsConstants.IDEMPOTENT_KEY_PREFIX + messageId;
        GlobalCache.key().removeCache(key);
    }
}
