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
 * 常用 Redis 封装场景的 {@link RedisCommandMeta} 预设，供 Manager 与文档引用。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-04-03
 */
public final class RedisOperationCatalog {

    private RedisOperationCatalog() {
    }

    /** GET/SET 单 key 单 value */
    public static final RedisCommandMeta STRING_SINGLE = RedisCommandMeta.o1("GET/SET 单 key，O(1)");

    /** MGET 多 key，O(k) */
    public static final RedisCommandMeta STRING_MULTI_GET = RedisCommandMeta.linear(
            "MGET 等，k 为 key 数量", false, false, false, false, false);

    /** HGET 单 field */
    public static final RedisCommandMeta HASH_FIELD = RedisCommandMeta.o1("HGET 单 field，O(1)");

    /** HGETALL / 全量 entries */
    public static final RedisCommandMeta HASH_FULL = RedisCommandMeta.linear(
            "HGETALL/entries，O(n) n 为 field 数", false, true, false, false, false);

    /** HKEYS */
    public static final RedisCommandMeta HASH_KEYS = RedisCommandMeta.linear(
            "HKEYS，O(n) n 为 field 数", false, true, false, false, false);

    /** HMGET 多 field */
    public static final RedisCommandMeta HASH_MULTI_FIELD = RedisCommandMeta.linear(
            "HMGET 等，k 为 field 数", false, false, false, false, false);

    /** LRANGE 等 */
    public static final RedisCommandMeta LIST_RANGE = RedisCommandMeta.linear(
            "LRANGE/LINDEX 等与区间/下标相关，注意范围", false, false, true, false, false);

    /** KEYS 模式匹配 */
    public static final RedisCommandMeta KEYS_PATTERN = RedisCommandMeta.linear(
            "KEYS pattern，O(键空间)，严禁 toC", false, false, false, false, false);

    /** ZRANK/ZRANGE 等 */
    public static final RedisCommandMeta ZSET_ORDERED = RedisCommandMeta.logN("ZSet 排名/区间，O(log n) 量级");

    /** ZUNIONSTORE / ZINTERSTORE / ZDIFF 等多 key 聚合 */
    public static final RedisCommandMeta ZSET_MULTI_KEY = RedisCommandMeta.linear(
            "ZSet 多 key 交并差，与参与集合元素规模相关", false, false, false, false, true);

    /** pipeline 批量 ZSet */
    public static final RedisCommandMeta ZSET_BATCH = RedisCommandMeta.linear(
            "pipeline 批量 ZSet，与批大小相关", false, false, false, false, true);

    /** SMEMBERS 全量 */
    public static final RedisCommandMeta SET_MEMBERS = RedisCommandMeta.linear(
            "SMEMBERS 全量，O(n)", false, false, false, true, false);

    /** SUNION/SINTER 多 key */
    public static final RedisCommandMeta SET_COMBINE = RedisCommandMeta.linear(
            "集合交并，规模与参与集合大小相关", false, false, false, true, false);

    /** GEO 半径 */
    public static final RedisCommandMeta GEO_RADIUS = RedisCommandMeta.linear(
            "GEO 查询，与范围内点数相关", false, false, false, false, false);

    /** GEOADD 单成员 */
    public static final RedisCommandMeta GEO_ADD = RedisCommandMeta.o1("GEOADD 单成员");

    /** GEODIST */
    public static final RedisCommandMeta GEO_DIST = RedisCommandMeta.o1("GEODIST 两点");

    /** EVAL */
    public static final RedisCommandMeta LUA_EVAL = RedisCommandMeta.script("EVAL 脚本，复杂度由脚本决定");

    /** 单 key 锁尝试 */
    public static final RedisCommandMeta LOCK_TRY = RedisCommandMeta.o1("SET NX/EX 等加锁尝试，O(1) 量级");

    /** Stream XADD */
    public static final RedisCommandMeta STREAM_XADD = RedisCommandMeta.script("Stream XADD，负载与序列化相关");

    /** Stream XACK */
    public static final RedisCommandMeta STREAM_ACK = RedisCommandMeta.o1("Stream XACK");

    /** Pub/Sub PUBLISH */
    public static final RedisCommandMeta PUBLISH = RedisCommandMeta.o1("PUBLISH 广播");

    /** SETBIT/GETBIT */
    public static final RedisCommandMeta BIT_OP = RedisCommandMeta.o1("SETBIT/GETBIT");

    /** PFADD，k 为元素个数 */
    public static final RedisCommandMeta HLL_PFADD = RedisCommandMeta.linear(
            "PFADD，k 为元素个数", false, false, false, false, false);

    /** PFCOUNT */
    public static final RedisCommandMeta HLL_PF_COUNT = RedisCommandMeta.o1("PFCOUNT");

    /** 注册 Pub/Sub 监听器（本地容器侧 O(1) 注册） */
    public static final RedisCommandMeta EVENT_SUBSCRIBE = RedisCommandMeta.o1("订阅注册");

    /** 限流 Lua */
    public static final RedisCommandMeta LIMITER_LUA = RedisCommandMeta.script("滑动窗口限流 Lua");
}
