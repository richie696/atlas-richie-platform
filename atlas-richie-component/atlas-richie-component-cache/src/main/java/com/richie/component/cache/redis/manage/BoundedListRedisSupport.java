package com.richie.component.cache.redis.manage;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import org.springframework.data.redis.connection.DataType;

/**
 * 有界队列 / 栈 Redis 管理器共用逻辑。
 */
final class BoundedListRedisSupport {

    private BoundedListRedisSupport() {
    }

    static void assertListKeyCompatible(MultiRedisTemplate<Object> redisTemplate, String key, String kind) {
        DataType type = redisTemplate.type(key);
        if (type == null || type == DataType.NONE || type == DataType.LIST) {
            return;
        }
        throw new IllegalStateException(
                "Key '%s' already exists as Redis %s, cannot create %s".formatted(key, type.code(), kind));
    }

    /**
     * @return {@code true} 新建成功；{@code false} 已存在
     */
    static boolean setMetaIfAbsent(MultiRedisTemplate<Object> redisTemplate, String metaKey, long maxLen) {
        Boolean set = redisTemplate.opsForValue().setIfAbsent(metaKey, String.valueOf(maxLen));
        if (Boolean.TRUE.equals(set)) {
            return true;
        }
        if (Boolean.FALSE.equals(set)) {
            return false;
        }
        throw new IllegalStateException("Failed to initialize bounded meta for: " + metaKey);
    }
}
