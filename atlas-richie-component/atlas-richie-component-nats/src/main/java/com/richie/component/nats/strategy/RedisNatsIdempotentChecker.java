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
