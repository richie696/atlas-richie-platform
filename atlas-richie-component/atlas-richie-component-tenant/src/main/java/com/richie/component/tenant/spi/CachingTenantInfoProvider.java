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
package com.richie.component.tenant.spi;

import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.monitor.TenantMetricsCollector;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 带 TTL 缓存的 {@link TenantInfoProvider} 装饰器。
 *
 * <p>拦截器层 / 策略层会在每次 SQL 执行前调用
 * {@link #getTenantInfo(Long)}，直接打到 {@code sys_tenant} 表会带来
 * N 次额外查询开销。本装饰器以 JDK {@link ConcurrentHashMap} 缓存结果，
 * 默认 {@code ttl-seconds=60}、{@code max-size=10000}。</p>
 *
 * <p><b>不缓存 {@code null}</b>：租户不存在时直接穿透到 delegate，避免恶意刷不存在
 * 租户把负向结果"锁死"在缓存里。</p>
 *
 * <p><b>不缓存 {@link #exists(Long)}</b>：存在性高频变化（开关 / 迁移中），实时穿透更安全。</p>
 *
 * <p>缓存淘汰：写时检查容量 + 周期性扫描过期（{@code ttl/2} 触发一次）。</p>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * TenantInfoProvider raw = new SysTenantInfoProvider(jdbcTemplate);
 * TenantInfoProvider cached = new CachingTenantInfoProvider(raw, 60, 10_000);
 * }</pre>
 *
 * <p>框架层通过 {@code @ConditionalOnProperty(prefix="multi-tenancy.cache.tenant-info",
 * name="enabled")} 自动装配，业务无需手动注册。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class CachingTenantInfoProvider implements TenantInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingTenantInfoProvider.class);

    private final TenantInfoProvider delegate;
    private final Duration ttl;
    private final int maxSize;
    private final TenantMetricsCollector metricsCollector;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService janitor;

    /**
     * 构造带 TTL 缓存的装饰器。
     *
     * @param delegate  底层真实 provider（必传，不能为 null）
     * @param ttlSeconds TTL（秒），{@code <= 0} 视为禁用缓存（直接穿透）
     * @param maxSize   最大缓存租户数，超出时清空一半（按 LRU 时间），{@code <= 0} 视为不限
     */
    public CachingTenantInfoProvider(TenantInfoProvider delegate, long ttlSeconds, int maxSize) {
        this(delegate, ttlSeconds, maxSize, null);
    }

    /**
     * 构造带 TTL 缓存的装饰器（含指标收集）。
     *
     * @param delegate         底层真实 provider（必传，不能为 null）
     * @param ttlSeconds       TTL（秒），{@code <= 0} 视为禁用缓存（直接穿透）
     * @param maxSize          最大缓存租户数，超出时清空一半（按 LRU 时间），{@code <= 0} 视为不限
     * @param metricsCollector 指标收集器（可为 {@code null}）
     */
    public CachingTenantInfoProvider(TenantInfoProvider delegate, long ttlSeconds, int maxSize,
                                     TenantMetricsCollector metricsCollector) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.ttl = ttlSeconds <= 0 ? Duration.ZERO : Duration.ofSeconds(ttlSeconds);
        this.maxSize = maxSize;
        this.metricsCollector = metricsCollector;
        if (this.ttl.isZero()) {
            this.janitor = null;
            log.info("CachingTenantInfoProvider created with TTL=0, caching disabled");
        } else {
            long sweepSeconds = Math.max(1, ttlSeconds / 2);
            this.janitor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "tenant-info-cache-janitor");
                t.setDaemon(true);
                return t;
            });
            this.janitor.scheduleWithFixedDelay(this::evictExpired, sweepSeconds, sweepSeconds, TimeUnit.SECONDS);
            log.info("CachingTenantInfoProvider created with ttl={}s, max-size={}, sweep={}s",
                ttlSeconds, maxSize, sweepSeconds);
        }
    }

    @Override
    public TenantInfo getTenantInfo(Long tenantId) {
        if (tenantId == null || ttl.isZero()) {
            return delegate.getTenantInfo(tenantId);
        }
        CacheEntry cached = cache.get(tenantId);
        if (cached != null && !cached.isExpired()) {
            if (metricsCollector != null) {
                metricsCollector.incrementCacheHits();
            }
            return cached.value;
        }
        TenantInfo fresh = delegate.getTenantInfo(tenantId);
        if (fresh != null) {
            if (metricsCollector != null) {
                metricsCollector.incrementCacheMisses();
            }
            cache.put(tenantId, new CacheEntry(fresh, Instant.now().plus(ttl)));
            enforceCapacity();
        }
        return fresh;
    }

    @Override
    public boolean exists(Long tenantId) {
        // 存在性高频变化，实时穿透
        return delegate.exists(tenantId);
    }

    /**
     * 失效单个租户的缓存（管理接口更新 sys_tenant 后调用）。
     *
     * @param tenantId 租户 ID
     */
    public void invalidate(Long tenantId) {
        if (tenantId != null) {
            cache.remove(tenantId);
        }
    }

    /**
     * 清空全部缓存。
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * 当前缓存大小（用于健康检查 / 监控指标）。
     */
    public int size() {
        return cache.size();
    }

    private void evictExpired() {
        try {
            Instant now = Instant.now();
            int before = cache.size();
            cache.entrySet().removeIf(e -> e.getValue().isExpiredAt(now));
            int after = cache.size();
            if (before != after) {
                log.debug("CachingTenantInfoProvider evicted {} expired entries ({} -> {})",
                    before - after, before, after);
            }
        } catch (Exception e) {
            log.warn("CachingTenantInfoProvider janitor failed: {}", e.getMessage());
        }
    }

    private void enforceCapacity() {
        if (maxSize <= 0 || cache.size() <= maxSize) {
            return;
        }
        // 简化策略：删掉过期项，若仍超限则按时间淘汰一半
        evictExpired();
        if (cache.size() > maxSize) {
            int toRemove = cache.size() - (maxSize / 2);
            cache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(toRemove)
                .forEach(e -> cache.remove(e.getKey()));
        }
    }

    @PreDestroy
    public void shutdown() {
        if (janitor != null) {
            janitor.shutdownNow();
        }
        cache.clear();
    }

    private record CacheEntry(TenantInfo value, Instant expiresAt) implements Comparable<CacheEntry> {

        boolean isExpired() {
            return isExpiredAt(Instant.now());
        }

        boolean isExpiredAt(Instant now) {
            return expiresAt.isBefore(now);
        }

        @Override
        public int compareTo(CacheEntry other) {
            return expiresAt.compareTo(other.expiresAt);
        }
    }
}
