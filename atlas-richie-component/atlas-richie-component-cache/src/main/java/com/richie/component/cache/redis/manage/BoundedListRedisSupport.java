package com.richie.component.cache.redis.manage;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;

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
     * 以 UTF-8 纯数字字符串写入 meta，供 Lua {@code tonumber(redis.call('GET', meta))} 读取。
     *
     * @return {@code true} 新建成功；{@code false} 已存在
     */
    static boolean setMetaIfAbsent(MultiRedisTemplate<Object> redisTemplate, String metaKey, long maxLen) {
        byte[] valueBytes = String.valueOf(maxLen).getBytes(StandardCharsets.UTF_8);
        Boolean set = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            byte[] keyBytes = serializeKey(redisTemplate, metaKey);
            return connection.stringCommands().setNX(keyBytes, valueBytes);
        });
        if (Boolean.TRUE.equals(set)) {
            return true;
        }
        if (Boolean.FALSE.equals(set)) {
            return false;
        }
        throw new IllegalStateException("Failed to initialize bounded meta for: " + metaKey);
    }

    private static byte[] serializeKey(MultiRedisTemplate<Object> redisTemplate, String metaKey) {
        var keySerializer = redisTemplate.getKeySerializer();
        if (keySerializer instanceof StringRedisSerializer stringRedisSerializer) {
            return stringRedisSerializer.serialize(metaKey);
        }
        return metaKey.getBytes(StandardCharsets.UTF_8);
    }
}
