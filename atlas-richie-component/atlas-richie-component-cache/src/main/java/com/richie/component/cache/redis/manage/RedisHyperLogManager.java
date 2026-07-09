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

import com.richie.component.cache.function.HyperLogFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * HyperLogLog相关API管理器，封装了Redis中HyperLogLog数据结构的常用操作。
 * <p>
 * 主要用于大规模基数统计（如UV、去重计数）等场景，具有极低内存消耗和可接受误差。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:47:07
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisHyperLogManager implements HyperLogFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    private final RedisPerfGuard redisPerfGuard;

    /**
     * 向HyperLogLog添加元素。
     *
     * @param key    HyperLogLog的键
     * @param values 要添加的元素，可变参数
     * @apiNote
     * <p><b>时间复杂度</b>：{@code O(k)}，{@code k} 为本次添加元素个数（PFADD）。
     * <p><b>严禁</b>：toC 单次调用传入极大 {@code k} 或高频连续 PFADD 造成 CPU/网络尖峰。
     * <p><b>可用</b>：UV、去重统计等可接受误差的计数场景。
     * <p><b>注意</b>：与精确 Set 去重不同，存在标准误差；勿用于金融级精确去重。
     */
    @Override
    public void pfAdd(String key, Object... values) {
        redisPerfGuard.execute("RedisHyperLogManager", "pfAdd", RedisOperationCatalog.HLL_PFADD,
                () -> redisTemplate.opsForHyperLogLog().add(key, values));
    }

    /**
     * 获取HyperLogLog的基数估算值。
     *
     * @param key HyperLogLog的键
     * @return 基数估算值（long类型）
     * @apiNote
     * <p><b>时间复杂度</b>：{@code O(1)}（PFCOUNT）。
     * <p><b>严禁</b>：无；注意结果为估算。
     * <p><b>可用</b>：报表、监控类读路径。
     * <p><b>注意</b>：大基数下误差在可接受范围内；热 key 读需配合缓存或采样。
     */
    @Override
    public long pfCount(String key) {
        return redisPerfGuard.<Long>execute("RedisHyperLogManager", "pfCount", RedisOperationCatalog.HLL_PF_COUNT,
                () -> redisTemplate.opsForHyperLogLog().size(key));
    }
}
