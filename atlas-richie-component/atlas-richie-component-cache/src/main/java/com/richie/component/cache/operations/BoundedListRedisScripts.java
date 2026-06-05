package com.richie.component.cache.operations;

import org.springframework.data.redis.core.script.RedisScript;

/**
 * 有界队列 / 栈共用的 Redis Lua 脚本。
 */
public final class BoundedListRedisScripts {

    /**
     * 容量翻倍：仅放大、不缩小。返回新 maxLen；已达 ceiling 返回 0；meta 不存在返回 -1；meta 非法返回 -2。
     */
    public static final RedisScript<Long> GROW_MAX_LEN_SCRIPT = RedisScript.of(
            "local current = tonumber(redis.call('GET', KEYS[1]));" +
            "if current == nil then return -1 end;" +
            "local ceiling = tonumber(ARGV[1]);" +
            "if current < 1 or current > ceiling then return -2 end;" +
            "if current >= ceiling then return 0 end;" +
            "local newMax = current * 2;" +
            "if newMax > ceiling then newMax = ceiling end;" +
            "redis.call('SET', KEYS[1], tostring(newMax));" +
            "return newMax;",
            Long.class);

    /**
     * 按 meta 中的 maxLen 裁剪 List（KEYS[1]=list, KEYS[2]=meta）。
     */
    public static final RedisScript<Long> TRIM_LIST_TO_META_SCRIPT = RedisScript.of(
            "local maxLen = tonumber(redis.call('GET', KEYS[2]));" +
            "if maxLen == nil then return -1 end;" +
            "redis.call('LTRIM', KEYS[1], -maxLen, -1);" +
            "return 1;",
            Long.class);

    /**
     * 同时为 list 与 meta 设置过期（毫秒，PEXPIRE）。KEYS[1]=list, KEYS[2]=meta。
     */
    public static final RedisScript<Long> PEXPIRE_BOTH_SCRIPT = RedisScript.of(
            "redis.call('PEXPIRE', KEYS[1], ARGV[1]);" +
            "redis.call('PEXPIRE', KEYS[2], ARGV[1]);" +
            "return 1;",
            Long.class);

    private BoundedListRedisScripts() {
    }
}
