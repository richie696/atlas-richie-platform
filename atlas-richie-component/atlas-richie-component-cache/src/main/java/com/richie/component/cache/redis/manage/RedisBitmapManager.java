/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.manage;

import com.richie.component.cache.function.BitmapFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Bitmap相关API管理器，封装了Redis中Bitmap位操作的常用方法。
 * <p>
 * 主要用于高效地进行布尔标记、用户签到、唯一性统计等场景。
 * 支持设置和获取指定key的某个位（bit）值。
 * <p>
 * 推荐配合布隆过滤器等高性能场景使用。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:38:43
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisBitmapManager implements BitmapFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    private final RedisPerfGuard redisPerfGuard;

    /**
     * 设置指定key的某个位（bit）值。
     *
     * @param key    Redis键
     * @param offset 位偏移（从0开始）
     * @param value  位值（true/false）
     * @apiNote
     * <p><b>时间复杂度</b>：{@code O(1)}（SETBIT）。
     * <p><b>严禁</b>：在 toC 热路径对超大 offset 无界循环写位（业务侧放大为 {@code O(k)}）。
     * <p><b>可用</b>：签到、去重标记、布隆相关位图等。
     * <p><b>注意</b>：大 key 时仍占内存与网络带宽；勿与全量扫描类操作混用在高 QPS 链路。
     */
    @Override
    public void setBit(String key, long offset, boolean value) {
        redisPerfGuard.execute("RedisBitmapManager", "setBit", RedisOperationCatalog.BIT_OP,
                () -> {
                    redisTemplate.opsForValue().setBit(key, offset, value);
                });
    }

    /**
     * 获取指定key的某个位（bit）值。
     *
     * @param key    Redis键
     * @param offset 位偏移（从0开始）
     * @return 指定位的布尔值，true表示1，false表示0
     * @apiNote
     * <p><b>时间复杂度</b>：{@code O(1)}（GETBIT）。
     * <p><b>严禁</b>：循环内对同一 key 大量随机 offset 读导致 RTT 放大。
     * <p><b>可用</b>：与 {@link #setBit} 对称的读路径。
     * <p><b>注意</b>：热 key 时关注单 key QPS 与连接池。
     */
    @Override
    public boolean getBit(String key, long offset) {
        return redisPerfGuard.<Boolean>execute("RedisBitmapManager", "getBit", RedisOperationCatalog.BIT_OP, () -> {
            Boolean result = redisTemplate.opsForValue().getBit(key, offset);
            return Boolean.TRUE.equals(result);
        });
    }
}
