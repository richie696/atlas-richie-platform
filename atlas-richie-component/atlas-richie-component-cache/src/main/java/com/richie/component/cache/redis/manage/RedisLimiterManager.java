/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.manage;

import com.richie.component.cache.ops.LimiterOps;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisComplexityTier;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Component;

/**
 * 分布式限流API管理器，封装了基于Redis的滑动窗口限流算法。
 * <p>
 * 适用于接口防刷、限流、突发流量控制等场景。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:49:40
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisLimiterManager implements LimiterOps {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    private final RedisPerfGuard redisPerfGuard;

    /**
     * 滑动窗口限流，判断是否允许通过。
     *
     * @param key         限流标识键
     * @param maxCount    窗口内最大请求数
     * @param windowSeconds 窗口时间（秒）
     * @return true表示允许通过，false表示被限流
     * @apiNote
     * <p><b>时间复杂度</b>：由 Lua 脚本决定，封装为 {@link RedisComplexityTier#SCRIPT_OR_UNKNOWN}。
     * <p><b>严禁</b>：在脚本未审计前将复杂 Lua 用于 toC 极高 QPS 且无本地合并。
     * <p><b>可用</b>：接口防刷、网关限流等。
     * <p><b>注意</b>：每次调用一次网络往返；热 key 与脚本耗时需监控（见 perf 阈值）。
     */
    @Override
    public boolean tryAcquire(String key, int maxCount, int windowSeconds) {
        return redisPerfGuard.<Boolean>execute("RedisLimiterManager", "tryAcquire", RedisOperationCatalog.LIMITER_LUA, () -> {
            String lua = "local c = redis.call('get', KEYS[1]) if c and tonumber(c) >= tonumber(ARGV[1])" +
                    " then return 0 else redis.call('incr', KEYS[1])" +
                    " redis.call('expire', KEYS[1], ARGV[2]) return 1 end";
            Long result = redisTemplate.execute((RedisCallback<Long>) conn ->
                conn.scriptingCommands().eval(lua.getBytes(), ReturnType.INTEGER, 1,
                        key.getBytes(), String.valueOf(maxCount).getBytes(), String.valueOf(windowSeconds).getBytes())
            );
            return result != null && result == 1;
        });
    }

}
