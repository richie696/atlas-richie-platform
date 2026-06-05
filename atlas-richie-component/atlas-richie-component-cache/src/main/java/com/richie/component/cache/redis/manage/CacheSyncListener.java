package com.richie.component.cache.redis.manage;

import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.function.HashFunction;
import com.richie.component.cache.function.SetFunction;
import com.richie.component.cache.function.StringFunction;
import com.richie.component.cache.ops.CacheInfrastructure;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Redis本地缓存同步监听器
 * <p>
 * 监听Redis的key事件（如del、set、expire等），实现本地缓存与Redis缓存的数据一致性。
 * 主要用于二级缓存场景下，自动感知Redis数据变更并同步刷新/删除本地缓存，防止脏读和数据不一致。
 * <ul>
 *   <li>onMessage：处理Redis事件消息，根据事件类型同步本地缓存</li>
 *   <li>isExpiredEvent：辅助判断是否为过期事件</li>
 * </ul>
 * <b>注意：</b> 仅在spring.data.redis.enableL2Caching=true时生效。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-16 03:12:39
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.data.redis", name = "enable-l2-caching", havingValue = "true")
public class CacheSyncListener implements MessageListener {

    /** String 类型操作，用于获取/刷新字符串缓存 */
    private final StringFunction stringFunction;

    /** 缓存框架内部基础设施（L2 开关、key 类型注册等） */
    private final CacheInfrastructure infra;

    /** Key 元操作（获取 Redis key 类型等） */
    private final KeyOps keyOps;

    /** Hash 类型操作，用于刷新 Hash 缓存 */
    private final HashFunction hashFunction;

    /** Set 类型操作，用于刷新 Set 缓存 */
    private final SetFunction setFunction;

    /** Redis 配置（含 Stream 幂等前缀等） */
    private final AtlasRedisProperties properties;

    /**
     * 处理Redis事件消息，根据事件类型同步本地缓存。
     *
     * @param message Redis消息体，包含key等信息
     * @param pattern 频道模式（如__keyevent@0__:del等）
     */
    @Override
    public void onMessage(@Nonnull Message message, byte[] pattern) {
        // 未启用L2缓存则直接返回
        if (!infra.enableL2Caching()) {
            return;
        }
        try {
            String key = new String(message.getBody());
            String channel = new String(pattern);

            // 忽略幂等去重相关键，避免无意义的本地缓存同步抖动
            if (isIdempotencyKey(key)) {
                if (log.isDebugEnabled()) {
                    log.debug("Cache sync: ignore idempotency key event: {} on {}", key, channel);
                }
                return;
            }
            // 根据不同的消息类型处理缓存同步
            if (channel.endsWith(":del")
                    || channel.endsWith(":unlink")
                    || channel.endsWith(":expired")) {
                // 删除/过期事件，直接删除本地缓存
                LocalCache.remove(L2CachingRegion.GLOBAL_CACHE, key);
                log.info("Cache sync: removed key {} from local cache due to Redis deletion", key);
            } else if (channel.endsWith(":set")
                    || isExpiredEvent(channel)) {
                // set/expire事件，刷新本地缓存
                var keyType = keyOps.getKeyType(key);
                if (keyType == null) {
                    log.warn("Cache sync: key {} not found in Redis, skipping sync", key);
                    return;
                }
                var valueType = infra.getValueType(key);
                // 根据key类型和value类型从Redis加载最新数据
                var value = switch (keyType) {
                    case STRING -> switch (valueType.getSimpleName()) {
                        case "Integer" -> stringFunction.getFromString(key, Integer.class);
                        case "Long" -> stringFunction.getFromString(key, Long.class);
                        case "Float" -> stringFunction.getFromString(key, Float.class);
                        case "Double" -> stringFunction.getFromString(key, Double.class);
                        case "Boolean" -> stringFunction.getFromString(key, Boolean.class);
                        case "String" -> stringFunction.getFromString(key, String.class);
                        default -> null;
                    };
                    case HASH -> hashFunction.getObjectFromHash(key, valueType);
                    case SET -> setFunction.getFromSet(key, valueType);
                    case LIST -> {
                        log.warn("Cache sync: LIST type key {} is not supported for local cache sync", key);
                        yield null;
                    }
                };
                if (value != null) {
                    // 更新本地缓存
                    LocalCache.put(L2CachingRegion.GLOBAL_CACHE, key, value);
                    log.info("Cache sync: refreshed key {} in local cache with new value from Redis", key);
                }
//                if (isExpiredEvent(channel)) {
//                    // 如果是expire事件，设置本地缓存的过期时间与Redis一致
//                    Long expireTime = keyFunction.getExpire(key);
//                    LocalCache.expiry(L2CachingRegion.GLOBAL_CACHE, key, expireTime, TimeUnit.SECONDS);
//                }
            }
        } catch (Exception e) {
            log.error("Error syncing cache: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断是否为Redis的expire事件
     *
     * @param channel 频道名
     * @return true：为expire事件
     */
    private boolean isExpiredEvent(String channel) {
        return channel.endsWith(":expire");
    }

    /**
     * 是否为幂等键，当前忽略前缀 idemp:stream:
     *
     * @param key Redis 键
     * @return true 表示是幂等键
     */
    /**
     * Stream MQ 幂等键前缀（与 {@code atlas-richie-component-redis-streammq} 默认一致），L2 失效时跳过。
     */
    private static final String STREAM_IDEMPOTENCY_KEY_PREFIX = "idemp:stream:";

    private boolean isIdempotencyKey(String key) {
        return key != null && key.startsWith(STREAM_IDEMPOTENCY_KEY_PREFIX);
    }
}
