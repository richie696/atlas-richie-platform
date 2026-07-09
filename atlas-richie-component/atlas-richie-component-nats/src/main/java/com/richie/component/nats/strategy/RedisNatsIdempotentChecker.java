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
package com.richie.component.nats.strategy;

import com.richie.component.cache.GlobalCache;
import com.richie.component.nats.NatsConstants;
import com.richie.component.nats.strategy.NatsIdempotentChecker;
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
@Slf4j
public class RedisNatsIdempotentChecker implements NatsIdempotentChecker {

    @Override
    public boolean isFirstTime(String messageId, long ttlMillis) {
        String key = NatsConstants.IDEMPOTENT_KEY_PREFIX + messageId;
        boolean success = GlobalCache.value().setIfAbsent(key, "1", ttlMillis);
        if (!success) {
            log.debug("Duplicate message detected, messageId={}", messageId);
        }
        return success;
    }

    @Override
    public void clear(String messageId) {
        String key = NatsConstants.IDEMPOTENT_KEY_PREFIX + messageId;
        GlobalCache.key().removeCache(key);
    }
}
