package com.richie.component.redis.streammq.stream;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Stream 幂等去重守卫：内存优先，Redis 兜底
 *
 * <p>为消费侧提供轻量的幂等控制，优先使用内存快速去重，失败时回退至 Redis SETNX 保障一次性处理。
 *
 * <p>主要功能：
 * <ul>
 *   <li>内存短期去重：极低开销，适合热点高并发</li>
 *   <li>Redis 兜底：跨实例去重，避免重复消费</li>
 *   <li>可配置 TTL 与前缀命名空间</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamIdempotencyGuard {

    /** Redis 模板 */
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Redis 配置 */
    private final AtlasRedisProperties properties;

    /** 内存已处理键缓存（key -> 过期时间戳） */
    private final Map<String, Long> inMemorySeen = new ConcurrentHashMap<>();

    /**
     * 尝试获取处理权。
     *
     * @param rawKey 业务幂等键（建议业务唯一键；无则可用 recordId）
     * @param ttlIfAbsent 若未配置则使用此 TTL
     * @return true 首次处理；false 重复，应该跳过
     */
    public boolean tryAcquire(String rawKey, Duration ttlIfAbsent) {
        Duration memoryTtl = getMemoryTtlOrDefault();
        Duration redisTtl = getRedisTtlOrDefault(ttlIfAbsent);

        String key = buildNamespacedKey(rawKey);
        long now = System.currentTimeMillis();
        long memExpiresAt = now + memoryTtl.toMillis();

        // 1) 内存快速去重
        Long seenUntil = inMemorySeen.get(key);
        if (seenUntil != null && seenUntil > now) {
            return false;
        }

        // 2) Redis SETNX 幂等
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", redisTtl);
            if (Boolean.TRUE.equals(ok)) {
                // 首次，占位成功
                inMemorySeen.put(key, memExpiresAt);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            // Redis 不可用时，退化为内存去重：写入内存并放行一次，防止全量拒绝
            log.warn("Idempotency Redis fallback, key={}", key, e);
            inMemorySeen.put(key, memExpiresAt);
            return true;
        }
    }

    private String buildNamespacedKey(String rawKey) {
        String prefix = properties.getStreamIdempotency() != null ? properties.getStreamIdempotency().getKeyPrefix() : "idemp:stream:";
        return prefix + Objects.requireNonNull(rawKey);
    }

    private Duration getMemoryTtlOrDefault() {
        if (properties.getStreamIdempotency() != null && properties.getStreamIdempotency().getMemoryTtl() != null) {
            return properties.getStreamIdempotency().getMemoryTtl();
        }
        return Duration.ofHours(1);
    }

    private Duration getRedisTtlOrDefault(Duration fallback) {
        if (properties.getStreamIdempotency() != null && properties.getStreamIdempotency().getRedisTtl() != null) {
            return properties.getStreamIdempotency().getRedisTtl();
        }
        return fallback != null ? fallback : Duration.ofHours(24);
    }
}
