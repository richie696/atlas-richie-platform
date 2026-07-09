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
package com.richie.component.cache.operations;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

    /**
     * 执行 Lua 脚本：KEYS 走 key 序列化，ARGV 以 UTF-8 纯字符串传递（避免 JSON 序列化导致 {@code tonumber} 失败）。
     */
    public static Long evalLong(MultiRedisTemplate<Object> redisTemplate, RedisScript<Long> script,
                                List<String> keys, String... rawArgs) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            byte[][] keysAndArgs = new byte[keys.size() + rawArgs.length][];
            for (int i = 0; i < keys.size(); i++) {
                keysAndArgs[i] = serializeKey(redisTemplate, keys.get(i));
            }
            for (int i = 0; i < rawArgs.length; i++) {
                keysAndArgs[keys.size() + i] = rawArgs[i].getBytes(StandardCharsets.UTF_8);
            }
            byte[] scriptBytes = script.getScriptAsString().getBytes(StandardCharsets.UTF_8);
            return evalScript(connection.scriptingCommands(), scriptBytes, keys.size(), keysAndArgs);
        });
    }

    private static Long evalScript(org.springframework.data.redis.connection.RedisScriptingCommands commands,
                                   byte[] scriptBytes, int numKeys, byte[][] keysAndArgs) {
        return switch (keysAndArgs.length) {
            case 0 -> commands.eval(scriptBytes, ReturnType.INTEGER, numKeys);
            case 1 -> commands.eval(scriptBytes, ReturnType.INTEGER, numKeys, keysAndArgs[0]);
            case 2 -> commands.eval(scriptBytes, ReturnType.INTEGER, numKeys, keysAndArgs[0], keysAndArgs[1]);
            case 3 -> commands.eval(scriptBytes, ReturnType.INTEGER, numKeys,
                    keysAndArgs[0], keysAndArgs[1], keysAndArgs[2]);
            default -> throw new IllegalArgumentException(
                    "Unsupported script arity: " + numKeys + " keys and " + (keysAndArgs.length - numKeys) + " args");
        };
    }

    private static byte[] serializeKey(MultiRedisTemplate<Object> redisTemplate, String key) {
        var keySerializer = redisTemplate.getKeySerializer();
        if (keySerializer instanceof StringRedisSerializer stringRedisSerializer) {
            return stringRedisSerializer.serialize(key);
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
