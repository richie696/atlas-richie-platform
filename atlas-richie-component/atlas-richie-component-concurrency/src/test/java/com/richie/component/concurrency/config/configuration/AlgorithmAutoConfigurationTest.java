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
package com.richie.component.concurrency.config.configuration;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.config.ConcurrencyProperties;
import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.PoolProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AlgorithmAutoConfiguration}.
 *
 * <p>使用 {@link AnnotationConfigApplicationContext} 作为轻量 Spring 容器，
 * 验证 {@link RateLimiter}、{@link CircuitBreaker} 与 {@link DynamicExecutor}
 * 多池注册的自动装配行为。</p>
 */
class AlgorithmAutoConfigurationTest {

    private ConfigurableApplicationContext context;

    @BeforeEach
    void setUp() {
        // 关闭时打印诊断日志禁掉，避免测试输出过噪
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 启动容器并加载 {@link AlgorithmAutoConfiguration} 与给定 in-memory 属性。
     */
    private void startContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfiguration.class);
        ctx.register(AlgorithmAutoConfiguration.class);
        if (properties != null && !properties.isEmpty()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(ctx, buildArgs(properties));
        }
        ctx.refresh();
        this.context = ctx;
    }

    private static String[] buildArgs(Map<String, Object> properties) {
        return properties.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
    }

    @Configuration
    static class TestConfiguration {
    }

    private ConfigurableListableBeanFactory beanFactory() {
        return (ConfigurableListableBeanFactory) context.getAutowireCapableBeanFactory();
    }

    // ============================================================================================
    // RateLimiter 自动装配
    // ============================================================================================

    @Nested
    @DisplayName("RateLimiter 自动装配")
    class RateLimiterAutoconfig {

        @Test
        @DisplayName("默认场景（未配置 enabled）：不创建 RateLimiter Bean")
        void default_disabled_createsNoBean() {
            startContext(null);

            // 默认不活跃
            assertThat(context.containsBean("rateLimiter")).isFalse();
        }

        @Test
        @DisplayName("enabled=true：创建 RateLimiter Bean，参数从配置读取")
        void enabled_createsBeanWithConfiguredProperties() {
            startContext(Map.of(
                    "platform.concurrency.rate-limiter.enabled", "true",
                    "platform.concurrency.rate-limiter.permits-per-second", "100"));

            assertThat(context.containsBean("rateLimiter")).isTrue();
            RateLimiter limiter = context.getBean("rateLimiter", RateLimiter.class);
            assertThat(limiter).isNotNull();
            // permitsPerSecond = 100 时，连续 acquire 多次应能拿到
            assertThat(limiter.tryAcquire()).isTrue();
        }

        @Test
        @DisplayName("enabled=false（显式）：不创建 RateLimiter Bean")
        void explicitlyDisabled_createsNoBean() {
            startContext(Map.of("platform.concurrency.rate-limiter.enabled", "false"));

            assertThat(context.containsBean("rateLimiter")).isFalse();
        }
    }

    // ============================================================================================
    // CircuitBreaker 自动装配
    // ============================================================================================

    @Nested
    @DisplayName("CircuitBreaker 自动装配")
    class CircuitBreakerAutoconfig {

        @Test
        @DisplayName("默认场景（未配置 enabled）：不创建 CircuitBreaker Bean")
        void default_disabled_createsNoBean() {
            startContext(null);
            assertThat(context.containsBean("circuitBreaker")).isFalse();
        }

        @Test
        @DisplayName("enabled=true：创建 CircuitBreaker Bean，可直接执行任务")
        void enabled_createsUsableBean() throws Exception {
            startContext(Map.of(
                    "platform.concurrency.circuit-breaker.enabled", "true",
                    "platform.concurrency.circuit-breaker.failure-rate-threshold", "0.5",
                    "platform.concurrency.circuit-breaker.sliding-window-size", "10",
                    "platform.concurrency.circuit-breaker.wait-duration", "1s"));

            assertThat(context.containsBean("circuitBreaker")).isTrue();
            CircuitBreaker breaker = context.getBean("circuitBreaker", CircuitBreaker.class);
            assertThat(breaker).isNotNull();

            String result = breaker.execute(() -> "ok", "fallback");
            assertThat(result).isEqualTo("ok");
        }
    }

    // ============================================================================================
    // DynamicExecutor 多池注册
    // ============================================================================================

    @Nested
    @DisplayName("DynamicExecutor 多池注册")
    class DynamicExecutorAutoconfig {

        @Test
        @DisplayName("空配置：不注册任何 DynamicExecutor Bean")
        void emptyConfig_noBean() {
            startContext(null);

            assertThat(context.getBeansOfType(DynamicExecutor.class)).isEmpty();
        }

        @Test
        @DisplayName("单个池：按池名注册 DynamicExecutor Bean，参数与配置一致")
        void singlePool_registersByName() {
            startContext(Map.of(
                    "platform.concurrency.thread-pools.order-executor.core-pool-size", "8",
                    "platform.concurrency.thread-pools.order-executor.maximum-pool-size", "16",
                    "platform.concurrency.thread-pools.order-executor.keep-alive-time", "30s",
                    "platform.concurrency.thread-pools.order-executor.queue-capacity", "500",
                    "platform.concurrency.thread-pools.order-executor.rejected-handler", "AbortPolicy"));

            assertThat(context.containsBean("order-executor")).isTrue();
            DynamicExecutor executor = context.getBean("order-executor", DynamicExecutor.class);
            assertThat(executor).isNotNull();
            assertThat(executor.getCorePoolSize()).isEqualTo(8);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(16);
            assertThat(executor.getKeepAliveTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(30);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(500);
            assertThat(executor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        }

        @Test
        @DisplayName("多池：每个池成为独立 Bean，可注入全部 Map<String, DynamicExecutor>")
        void multiplePools_eachRegisteredSeparately() {
            startContext(Map.of(
                    "platform.concurrency.thread-pools.pool-a.core-pool-size", "2",
                    "platform.concurrency.thread-pools.pool-a.maximum-pool-size", "4",
                    "platform.concurrency.thread-pools.pool-b.core-pool-size", "4",
                    "platform.concurrency.thread-pools.pool-b.maximum-pool-size", "8"));

            assertThat(context.containsBean("pool-a")).isTrue();
            assertThat(context.containsBean("pool-b")).isTrue();

            Map<String, DynamicExecutor> beans = context.getBeansOfType(DynamicExecutor.class);
            assertThat(beans.keySet()).containsExactlyInAnyOrder("pool-a", "pool-b");

            // 各自参数独立
            assertThat(beans.get("pool-a").getMaximumPoolSize()).isEqualTo(4);
            assertThat(beans.get("pool-b").getMaximumPoolSize()).isEqualTo(8);
        }

        @Test
        @DisplayName("显式 threadNamePrefix：使用配置前缀而非池名")
        void explicitThreadNamePrefix_isUsedInsteadOfPoolName() {
            startContext(Map.of(
                    "platform.concurrency.thread-pools.order-executor.core-pool-size", "2",
                    "platform.concurrency.thread-pools.order-executor.maximum-pool-size", "4",
                    "platform.concurrency.thread-pools.order-executor.thread-name-prefix", "custom-prefix-"));

            DynamicExecutor executor = context.getBean("order-executor", DynamicExecutor.class);
            assertThat(executor).isNotNull();

            // 先执行一个任务让工厂创建线程
            executor.execute(() -> {});
            Thread[] threads = new Thread[Thread.activeCount() + 8];
            int n = Thread.enumerate(threads);
            boolean foundPrefix = false;
            for (int i = 0; i < n; i++) {
                if (threads[i] != null && threads[i].getName().startsWith("custom-prefix-")) {
                    foundPrefix = true;
                    break;
                }
            }
            assertThat(foundPrefix).isTrue();
        }

        @Test
        @DisplayName("rejected-handler=CallerRunsPolicy：使用 CallerRunsPolicy 而非默认 AbortPolicy")
        void customRejectedHandler_callerRuns() {
            startContext(Map.of(
                    "platform.concurrency.thread-pools.pool-a.core-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-a.maximum-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-a.keep-alive-time", "60s",
                    "platform.concurrency.thread-pools.pool-a.queue-capacity", "1",
                    "platform.concurrency.thread-pools.pool-a.rejected-handler", "CallerRunsPolicy"));

            DynamicExecutor executor = context.getBean("pool-a", DynamicExecutor.class);
            assertThat(executor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        }

        @Test
        @DisplayName("不支持的 rejected-handler：启动时抛 IllegalArgumentException")
        void unsupportedRejectedHandler_failsFast() {
            assertThatThrownBy(() -> startContext(Map.of(
                    "platform.concurrency.thread-pools.bad.core-pool-size", "1",
                    "platform.concurrency.thread-pools.bad.maximum-pool-size", "1",
                    "platform.concurrency.thread-pools.bad.rejected-handler", "UnsupportedPolicy")))
                    .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejected-handler 大小写不敏感：callerrunspolicy 等同 CallerRunsPolicy")
        void rejectedHandlerIsCaseInsensitive() {
            startContext(Map.of(
                    "platform.concurrency.thread-pools.pool-a.core-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-a.maximum-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-a.rejected-handler", "discardpolicy"));

            DynamicExecutor executor = context.getBean("pool-a", DynamicExecutor.class);
            assertThat(executor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.DiscardPolicy.class);
        }

        @Test
        @DisplayName("容器关闭时：所有 DynamicExecutor 都已 shutdown")
        void contextClose_shutsDownAllExecutors() {
            startContext(Map.of(
                    "platform.concurrency.thread-pools.pool-a.core-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-a.maximum-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-b.core-pool-size", "1",
                    "platform.concurrency.thread-pools.pool-b.maximum-pool-size", "1"));

            DynamicExecutor a = context.getBean("pool-a", DynamicExecutor.class);
            DynamicExecutor b = context.getBean("pool-b", DynamicExecutor.class);

            assertThat(a.isShutdown()).isFalse();
            assertThat(b.isShutdown()).isFalse();

            context.close();
            context = null; // 已显式关闭，避免 @AfterEach 重复

            assertThat(a.isShutdown()).isTrue();
            assertThat(b.isShutdown()).isTrue();
        }
    }

    // ============================================================================================
    // 配置属性映射
    // ============================================================================================

    @Nested
    @DisplayName("配置属性映射")
    class PropertiesBinding {

        @Test
        @DisplayName("ConcurrencyProperties 完整绑定（rateLimiter + circuitBreaker + threadPools）")
        void concurrencyProperties_bound() {
            startContext(Map.of(
                    "platform.concurrency.rate-limiter.permits-per-second", "200",
                    "platform.concurrency.circuit-breaker.failure-rate-threshold", "0.6",
                    "platform.concurrency.circuit-breaker.sliding-window-size", "100",
                    "platform.concurrency.circuit-breaker.wait-duration", "15s"));

            ConcurrencyProperties props = context.getBean(ConcurrencyProperties.class);
            assertThat(props.getRateLimiter().getPermitsPerSecond()).isEqualTo(200);
            assertThat(props.getCircuitBreaker().getFailureRateThreshold()).isEqualTo(0.6);
            assertThat(props.getCircuitBreaker().getSlidingWindowSize()).isEqualTo(100);
            assertThat(props.getCircuitBreaker().getWaitDuration())
                    .isEqualTo(java.time.Duration.ofSeconds(15));
            assertThat(props.getThreadPools()).isEmpty();
        }

        @Test
        @DisplayName("子 Properties 类分别绑定")
        void subPropertiesBound() {
            startContext(null);

            RateLimiterProperties rl = context.getBean(RateLimiterProperties.class);
            CircuitBreakerProperties cb = context.getBean(CircuitBreakerProperties.class);
            assertThat(rl).isNotNull();
            assertThat(cb).isNotNull();
        }
    }
}
