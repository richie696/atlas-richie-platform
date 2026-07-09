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
package com.richie.component.tenant.monitor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 多租户组件内建指标收集器（轻量无锁计数器）。
 *
 * <p>提供 SQL 改写、缓存命中等领域的基础计数，由 {@link TenantMeterBinder}
 * 消费并注册为 Micrometer Meter。设计为纯 {@link AtomicLong} 累加，
 * 不依赖 Spring，不依赖 MeterRegistry，确保在 Micrometer 未引入时零开销。</p>
 *
 * <p><b>线程安全</b>：所有计数器均为 {@link AtomicLong}。</p>
 *
 * <h2>指标一览</h2>
 * <table border="1">
 * <caption>指标列表</caption>
 * <tr><th>计数器</th><th>标签</th><th>说明</th></tr>
 * <tr><td>{@code tenant.sql.rewrite.attempts}</td><td>type=line|table</td><td>SQL 改写尝试次数</td></tr>
 * <tr><td>{@code tenant.sql.rewrite.success}</td><td>type=line|table</td><td>SQL 改写成功次数</td></tr>
 * <tr><td>{@code tenant.cache.hits}</td><td>—</td><td>{@link com.richie.component.tenant.spi.CachingTenantInfoProvider} 缓存命中</td></tr>
 * <tr><td>{@code tenant.cache.misses}</td><td>—</td><td>缓存未命中（穿透到 delegate）</td></tr>
 * </table>
 *
 * @author richie696
 * @since 1.0.0
 */
public class TenantMetricsCollector {

    // ==================== SQL 改写计数器（line / table） ====================

    private final AtomicLong lineRewriteAttempts = new AtomicLong();
    private final AtomicLong lineRewriteSuccess = new AtomicLong();
    private final AtomicLong tableRewriteAttempts = new AtomicLong();
    private final AtomicLong tableRewriteSuccess = new AtomicLong();

    // ==================== 缓存计数器 ====================

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

    // ==================== SQL 改写：line ====================

    /** 递增一次 Column 模式 SQL 改写尝试。 */
    public void incrementLineRewriteAttempts() {
        lineRewriteAttempts.incrementAndGet();
    }

    /** 递增一次 Column 模式 SQL 改写成功。 */
    public void incrementLineRewriteSuccess() {
        lineRewriteSuccess.incrementAndGet();
    }

    public long getLineRewriteAttempts() {
        return lineRewriteAttempts.get();
    }

    public long getLineRewriteSuccess() {
        return lineRewriteSuccess.get();
    }

    // ==================== SQL 改写：table ====================

    /** 递增一次 Table 模式 SQL 改写尝试。 */
    public void incrementTableRewriteAttempts() {
        tableRewriteAttempts.incrementAndGet();
    }

    /** 递增一次 Table 模式 SQL 改写成功。 */
    public void incrementTableRewriteSuccess() {
        tableRewriteSuccess.incrementAndGet();
    }

    public long getTableRewriteAttempts() {
        return tableRewriteAttempts.get();
    }

    public long getTableRewriteSuccess() {
        return tableRewriteSuccess.get();
    }

    // ==================== 缓存 ====================

    /** 递增一次缓存命中。 */
    public void incrementCacheHits() {
        cacheHits.incrementAndGet();
    }

    /** 递增一次缓存未命中（穿透到 delegate）。 */
    public void incrementCacheMisses() {
        cacheMisses.incrementAndGet();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * 缓存命中率（0.0 ~ 1.0），无数据时返回 0.0。
     */
    public double getCacheHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
