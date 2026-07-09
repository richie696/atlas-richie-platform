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
package com.richie.component.cache.redis.perf;

/**
 * 单条 Redis 封装调用的元数据：复杂度分级、BIGKEY 探测建议、文档说明。
 * <p>用于 {@link RedisPerfGuard} 与 JavaDoc 生成/人工校对时的统一口径。
 *
 * @param tier                    复杂度分级（与 ToC 策略对齐）
 * @param suggestStrlenProbe      读 String 全量前建议 STRLEN / MEMORY USAGE
 * @param suggestHlenProbe        读 Hash 全量前建议 HLEN
 * @param suggestLlenProbe        读 List 全量/大范围前建议 LLEN
 * @param suggestScardProbe       读 Set 全量前建议 SCARD
 * @param suggestZcardProbe       读 ZSet 大范围前建议 ZCARD
 * @param description             简短说明（Redis 命令主导项、变量 n/k 含义）
 * @author richie696
 * @version 1.0.0
 * @since 2026-04-03
 */
public record RedisCommandMeta(
        RedisComplexityTier tier,
        boolean suggestStrlenProbe,
        boolean suggestHlenProbe,
        boolean suggestLlenProbe,
        boolean suggestScardProbe,
        boolean suggestZcardProbe,
        String description
) {

    /**
     * 无额外探测提示的 O(1) 操作。
     */
    public static RedisCommandMeta o1(String description) {
        return new RedisCommandMeta(RedisComplexityTier.O1, false, false, false, false, false, description);
    }

    /**
     * 有序结构，典型 O(log n)。
     */
    public static RedisCommandMeta logN(String description) {
        return new RedisCommandMeta(RedisComplexityTier.LOG_N, false, false, false, false, true, description);
    }

    /**
     * 与元素数或扫描规模线性相关。
     */
    public static RedisCommandMeta linear(
            String description,
            boolean strlen,
            boolean hlen,
            boolean llen,
            boolean scard,
            boolean zcard) {
        return new RedisCommandMeta(
                RedisComplexityTier.LINEAR_N, strlen, hlen, llen, scard, zcard, description);
    }

    /**
     * Lua / 自定义脚本，复杂度由脚本内容决定。
     */
    public static RedisCommandMeta script(String description) {
        return new RedisCommandMeta(RedisComplexityTier.SCRIPT_OR_UNKNOWN, false, false, false, false, false, description);
    }

    /**
     * 理论更差或明确禁止在 ToC 使用的封装（若仍存在则标 WORSE）。
     */
    public static RedisCommandMeta worse(String description) {
        return new RedisCommandMeta(RedisComplexityTier.WORSE, false, false, false, false, false, description);
    }
}
