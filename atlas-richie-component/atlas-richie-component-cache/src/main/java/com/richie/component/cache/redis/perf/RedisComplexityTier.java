package com.richie.component.cache.redis.perf;

/**
 * Redis 操作在 ToC 场景下的复杂度分级（用于文档与 {@link RedisPerfGuard}）。
 * <p>总序：{@code O(1) > O(log n) > O(n) > O(n log n) > O(n²) > O(2ⁿ) > …}
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-04-03
 */
public enum RedisComplexityTier {

    /**
     * 常数级：核心链路推荐。
     */
    O1,

    /**
     * 对数级：有序结构（如 ZSet）相关，可控范围内可用。
     */
    LOG_N,

    /**
     * 线性级：与元素数、扫描范围相关；ToC 高并发下默认禁用或仅离线/管理端。
     */
    LINEAR_N,

    /**
     * 更差（{@code O(n log n)}、{@code O(n²)}、指数级等）：严禁在 ToC 核心路径使用。
     */
    WORSE,

    /**
     * 由脚本/自定义逻辑决定（如 Lua），无法在封装层统一归类。
     */
    SCRIPT_OR_UNKNOWN
}
