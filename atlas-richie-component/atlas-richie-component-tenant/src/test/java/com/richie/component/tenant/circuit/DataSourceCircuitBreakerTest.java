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
package com.richie.component.tenant.circuit;

import com.richie.component.tenant.config.MultiTenancyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataSourceCircuitBreaker — 数据源熔断器状态机")
class DataSourceCircuitBreakerTest {

    private MultiTenancyProperties props;
    private DataSourceCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        props = new MultiTenancyProperties();
        // 降低阈值便于测试
        props.getCircuit().setFailureThreshold(3);
        props.getCircuit().setOpenWindowMs(100); // 100ms 快速超时
        breaker = new DataSourceCircuitBreaker(props);
    }

    @Nested
    @DisplayName("初始状态")
    class InitialState {

        @Test
        @DisplayName("未注册 key 时 isOpen 返回 false")
        void unknownKeyIsNotOpen() {
            assertThat(breaker.isOpen("unknown")).isFalse();
        }

        @Test
        @DisplayName("未注册 key 时 getStatus 返回 CLOSED")
        void unknownKeyStatusIsClosed() {
            assertThat(breaker.getStatus("unknown")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN")
    class ClosedToOpen {

        @Test
        @DisplayName("连续失败达阈值后转为 OPEN")
        void failuresExceedThresholdOpensCircuit() {
            breaker.recordFailure("ds-1");
            breaker.recordFailure("ds-1");
            assertThat(breaker.isOpen("ds-1")).isFalse();

            breaker.recordFailure("ds-1");
            assertThat(breaker.isOpen("ds-1")).isTrue();
            assertThat(breaker.getStatus("ds-1")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("OPEN → HALF_OPEN")
    class OpenToHalfOpen {

        @Test
        @DisplayName("超过 openWindowMs 后转为 HALF_OPEN")
        void afterTimeoutTransitionsToHalfOpen() throws Exception {
            breaker.recordFailure("ds-2");
            breaker.recordFailure("ds-2");
            breaker.recordFailure("ds-2");
            assertThat(breaker.isOpen("ds-2")).isTrue();

            // 等待超过 openWindowMs (100ms)
            Thread.sleep(150);

            assertThat(breaker.isOpen("ds-2")).isFalse();
            assertThat(breaker.getStatus("ds-2")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("HALF_OPEN → CLOSED")
    class HalfOpenToClosed {

        @Test
        @DisplayName("HALF_OPEN 状态下 recordSuccess 转为 CLOSED")
        void successInHalfOpenClosesCircuit() throws Exception {
            breaker.recordFailure("ds-3");
            breaker.recordFailure("ds-3");
            breaker.recordFailure("ds-3");
            Thread.sleep(150);
            // isOpen() 触发 OPEN → HALF_OPEN 转换
            assertThat(breaker.isOpen("ds-3")).isFalse();
            assertThat(breaker.getStatus("ds-3")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.HALF_OPEN);

            breaker.recordSuccess("ds-3");
            assertThat(breaker.getStatus("ds-3")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("回调")
    class Callbacks {

        @Test
        @DisplayName("onOpen 回调在 OPEN 时触发")
        void onOpenCallbackFires() {
            AtomicReference<String> openedKey = new AtomicReference<>();
            breaker.onOpen(openedKey::set);

            breaker.recordFailure("ds-4");
            breaker.recordFailure("ds-4");
            breaker.recordFailure("ds-4");

            assertThat(openedKey.get()).isEqualTo("ds-4");
        }

        @Test
        @DisplayName("onClose 回调在 CLOSED 时触发")
        void onCloseCallbackFires() throws Exception {
            AtomicReference<String> closedKey = new AtomicReference<>();
            breaker.onClose(closedKey::set);

            breaker.recordFailure("ds-5");
            breaker.recordFailure("ds-5");
            breaker.recordFailure("ds-5");
            Thread.sleep(150);
            // isOpen() 触发 OPEN → HALF_OPEN
            breaker.isOpen("ds-5");
            breaker.recordSuccess("ds-5");

            assertThat(closedKey.get()).isEqualTo("ds-5");
        }
    }

    @Nested
    @DisplayName("getAllStatuses")
    class AllStatuses {

        @Test
        @DisplayName("返回所有已注册数据源的状态快照")
        void returnsAllStatuses() {
            breaker.recordFailure("ds-a");
            breaker.recordFailure("ds-b");
            breaker.recordFailure("ds-b");
            breaker.recordFailure("ds-b");

            Map<String, DataSourceCircuitBreaker.CircuitStatusSnapshot> all = breaker.getAllStatuses();
            assertThat(all).containsKeys("ds-a", "ds-b");
            assertThat(all.get("ds-b").status()).isEqualTo("OPEN");
        }
    }

    @Nested
    @DisplayName("并发安全(v1.0.0 修复)")
    class ConcurrencySafety {

        @Test
        @DisplayName("100 并发 recordFailure 不丢计数")
        void concurrentFailureCounting() throws Exception {
            int threads = 100;
            int failuresPerThread = 50;
            ExecutorService executor =
                Executors.newFixedThreadPool(16);
            CountDownLatch latch =
                new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < failuresPerThread; j++) {
                            breaker.recordFailure("ds-concurrent");
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // 应至少触发一次 OPEN(总失败数远超阈值 3)
            assertThat(breaker.isOpen("ds-concurrent")).isTrue();
            assertThat(breaker.getStatus("ds-concurrent"))
                .isEqualTo(DataSourceCircuitBreaker.CircuitStatus.OPEN);

            // onOpen 回调只触发一次
            AtomicInteger openCount =
                new AtomicInteger(0);
            DataSourceCircuitBreaker breaker2 = new DataSourceCircuitBreaker(props);
            breaker2.onOpen(k -> openCount.incrementAndGet());

            CountDownLatch latch2 =
                new CountDownLatch(threads);
            ExecutorService executor2 =
                Executors.newFixedThreadPool(16);
            for (int i = 0; i < threads; i++) {
                executor2.submit(() -> {
                    try {
                        for (int j = 0; j < failuresPerThread; j++) {
                            breaker2.recordFailure("ds-cb");
                        }
                    } finally {
                        latch2.countDown();
                    }
                });
            }
            latch2.await(10, TimeUnit.SECONDS);
            executor2.shutdown();

            assertThat(openCount.get())
                .as("OPEN 状态翻转的 CAS 保护 — onOpen 回调应仅触发一次")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("OPEN → HALF_OPEN 翻转仅一个探测请求通过")
        void halfOpenAllowsOnlyOneProbe() throws Exception {
            breaker.recordFailure("ds-probe");
            breaker.recordFailure("ds-probe");
            breaker.recordFailure("ds-probe");
            assertThat(breaker.isOpen("ds-probe")).isTrue();

            // 等到超时
            Thread.sleep(150);

            // 100 并发 isOpen 调用:只一个返回 false (HALF_OPEN 探测)
            int threads = 100;
            ExecutorService executor =
                Executors.newFixedThreadPool(16);
            CountDownLatch latch =
                new CountDownLatch(threads);
            AtomicInteger allowedProbes =
                new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        if (!breaker.isOpen("ds-probe")) {
                            allowedProbes.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(allowedProbes.get())
                .as("HALF_OPEN 状态应仅放行 1 个探测请求")
                .isEqualTo(1);
            assertThat(breaker.getStatus("ds-probe"))
                .isEqualTo(DataSourceCircuitBreaker.CircuitStatus.HALF_OPEN);
        }

        @Test
        @DisplayName("CLOSED 态下成功调用清零 failureCount,避免零星失败累加误熔断")
        void successInClosedResetsFailureCount() {
            breaker.recordFailure("ds-reset");
            breaker.recordFailure("ds-reset");
            assertThat(breaker.getAllStatuses().get("ds-reset").failures()).isEqualTo(2);

            breaker.recordSuccess("ds-reset");
            assertThat(breaker.getAllStatuses().get("ds-reset").failures())
                .as("CLOSED 态成功应清零失败计数")
                .isEqualTo(0);

            // 重新累计失败,需要达到阈值才能熔断
            breaker.recordFailure("ds-reset");
            breaker.recordFailure("ds-reset");
            assertThat(breaker.isOpen("ds-reset"))
                .as("清零后,需要重新累计 3 次才会熔断")
                .isFalse();
        }
    }
}
