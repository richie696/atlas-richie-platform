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
package com.richie.component.tenant.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantMetricsCollector — 指标计数器")
class TenantMetricsCollectorTest {

    private TenantMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new TenantMetricsCollector();
    }

    @Nested
    @DisplayName("SQL 改写计数")
    class SqlRewriteCounters {

        @Test
        @DisplayName("line rewrite attempts 初始为 0")
        void initialLineAttemptsIsZero() {
            assertThat(collector.getLineRewriteAttempts()).isZero();
        }

        @Test
        @DisplayName("incrementLineRewriteAttempts 递增并返回最新值")
        void incrementLineAttempts() {
            collector.incrementLineRewriteAttempts();
            assertThat(collector.getLineRewriteAttempts()).isEqualTo(1);
            collector.incrementLineRewriteAttempts();
            assertThat(collector.getLineRewriteAttempts()).isEqualTo(2);
            collector.incrementLineRewriteAttempts();
            assertThat(collector.getLineRewriteAttempts()).isEqualTo(3);
        }

        @Test
        @DisplayName("line rewrite success 初始为 0")
        void initialLineSuccessIsZero() {
            assertThat(collector.getLineRewriteSuccess()).isZero();
        }

        @Test
        @DisplayName("incrementLineRewriteSuccess 递增")
        void incrementLineSuccess() {
            collector.incrementLineRewriteSuccess();
            assertThat(collector.getLineRewriteSuccess()).isEqualTo(1);
            collector.incrementLineRewriteSuccess();
            assertThat(collector.getLineRewriteSuccess()).isEqualTo(2);
        }

        @Test
        @DisplayName("table rewrite attempts 初始为 0")
        void initialTableAttemptsIsZero() {
            assertThat(collector.getTableRewriteAttempts()).isZero();
        }

        @Test
        @DisplayName("incrementTableRewriteAttempts 递增")
        void incrementTableAttempts() {
            collector.incrementTableRewriteAttempts();
            assertThat(collector.getTableRewriteAttempts()).isEqualTo(1);
            collector.incrementTableRewriteAttempts();
            assertThat(collector.getTableRewriteAttempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("table rewrite success 初始为 0")
        void initialTableSuccessIsZero() {
            assertThat(collector.getTableRewriteSuccess()).isZero();
        }

        @Test
        @DisplayName("incrementTableRewriteSuccess 递增")
        void incrementTableSuccess() {
            collector.incrementTableRewriteSuccess();
            assertThat(collector.getTableRewriteSuccess()).isEqualTo(1);
            collector.incrementTableRewriteSuccess();
            assertThat(collector.getTableRewriteSuccess()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("缓存计数")
    class CacheCounters {

        @Test
        @DisplayName("cache hits 初始为 0")
        void initialHitsIsZero() {
            assertThat(collector.getCacheHits()).isZero();
        }

        @Test
        @DisplayName("incrementCacheHits 递增")
        void incrementHits() {
            collector.incrementCacheHits();
            assertThat(collector.getCacheHits()).isEqualTo(1);
            collector.incrementCacheHits();
            assertThat(collector.getCacheHits()).isEqualTo(2);
        }

        @Test
        @DisplayName("cache misses 初始为 0")
        void initialMissesIsZero() {
            assertThat(collector.getCacheMisses()).isZero();
        }

        @Test
        @DisplayName("incrementCacheMisses 递增")
        void incrementMisses() {
            collector.incrementCacheMisses();
            assertThat(collector.getCacheMisses()).isEqualTo(1);
            collector.incrementCacheMisses();
            assertThat(collector.getCacheMisses()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("命中率计算")
    class HitRatio {

        @Test
        @DisplayName("无数据时命中率为 0")
        void noDataRatioIsZero() {
            assertThat(collector.getCacheHitRatio()).isZero();
        }

        @Test
        @DisplayName("全命中时命中率为 1.0")
        void allHitsRatioIsOne() {
            collector.incrementCacheHits();
            collector.incrementCacheHits();
            assertThat(collector.getCacheHitRatio()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("命中率和失败率互补")
        void ratioWithMixedData() {
            collector.incrementCacheHits();
            collector.incrementCacheHits();
            collector.incrementCacheHits();
            collector.incrementCacheMisses();
            // 3 hits, 1 miss → 75%
            assertThat(collector.getCacheHitRatio()).isEqualTo(0.75);
        }

        @Test
        @DisplayName("全失败时命中率为 0")
        void allMissesRatioIsZero() {
            collector.incrementCacheMisses();
            assertThat(collector.getCacheHitRatio()).isZero();
        }
    }

    @Nested
    @DisplayName("并发安全")
    class Concurrency {

        @Test
        @DisplayName("多线程并发递增不丢计数")
        void concurrentIncrements() throws Exception {
            int threadCount = 10;
            int incrementsPerThread = 1000;
            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                exec.submit(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            collector.incrementLineRewriteAttempts();
                            collector.incrementCacheHits();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            exec.shutdown();

            assertThat(collector.getLineRewriteAttempts()).isEqualTo(threadCount * incrementsPerThread);
            assertThat(collector.getCacheHits()).isEqualTo(threadCount * incrementsPerThread);
        }
    }
}
