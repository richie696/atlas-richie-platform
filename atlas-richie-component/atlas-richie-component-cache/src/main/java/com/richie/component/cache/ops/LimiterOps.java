package com.richie.component.cache.ops;

/**
 * 分布式限流API管理器，封装了基于Redis的滑动窗口限流算法。
 * <p>
 * 适用于接口防刷、限流、突发流量控制等场景。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:49:40
 */
public interface LimiterOps {

    /**
     * 滑动窗口限流，判断是否允许通过。
     *
     * @param key            限流标识键
     * @param maxCount       窗口内最大请求数
     * @param windowSeconds  窗口时间（秒）
     * @return true表示允许通过，false表示被限流
     */
    boolean tryAcquire(String key, int maxCount, int windowSeconds);
}
