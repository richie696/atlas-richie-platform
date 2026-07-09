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
package com.richie.component.concurrency.config;

import com.richie.component.concurrency.config.properties.PoolProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import com.richie.component.concurrency.threadpool.ThreadPoolConfigRefresher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ThreadPoolConfigRefresher}.
 *
 * <p>使用真实的 {@link StandardEnvironment} + {@link Binder} 而非 Mock，确保
 * 配置重新绑定的完整路径正确。模拟配置变更通过修改 Environment 的 PropertySource 实现。</p>
 */
class ThreadPoolConfigRefresherTest {

    private ConfigurableEnvironment env;
    private Binder binder;
    private DynamicExecutor orderExecutor;
    private DynamicExecutor notifyExecutor;
    private Map<String, DynamicExecutor> executors;
    private ConcurrencyProperties properties;
    private ThreadPoolConfigRefresher refresher;

    private static final String ORDER = "order-executor";
    private static final String NOTIFY = "notification-executor";
    private static final String PREFIX = "platform.concurrency.thread-pools";

    @BeforeEach
    void setUp() {
        // ============================================================
        // 1. 准备 Environment（初始配置）
        // ============================================================
        env = new StandardEnvironment();
        Map<String, Object> initial = new LinkedHashMap<>();
        // order-executor: core=4, max=8, keepAlive=60s, queue=500, handler=AbortPolicy
        initial.put(key(ORDER, "core-pool-size"), "4");
        initial.put(key(ORDER, "maximum-pool-size"), "8");
        initial.put(key(ORDER, "keep-alive-time"), "60s");
        initial.put(key(ORDER, "queue-capacity"), "500");
        initial.put(key(ORDER, "rejected-handler"), "AbortPolicy");
        // notification-executor: core=2, max=4, keepAlive=30s, queue=200, handler=DiscardPolicy
        initial.put(key(NOTIFY, "core-pool-size"), "2");
        initial.put(key(NOTIFY, "maximum-pool-size"), "4");
        initial.put(key(NOTIFY, "keep-alive-time"), "30s");
        initial.put(key(NOTIFY, "queue-capacity"), "200");
        initial.put(key(NOTIFY, "rejected-handler"), "DiscardPolicy");
        env.getPropertySources().addFirst(new MapPropertySource("test", initial));

        binder = Binder.get(env);

        // ============================================================
        // 2. 创建 DynamicExecutor
        // ============================================================
        orderExecutor = new DynamicExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500));
        notifyExecutor = new DynamicExecutor(2, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200));

        executors = new LinkedHashMap<>();
        executors.put(ORDER, orderExecutor);
        executors.put(NOTIFY, notifyExecutor);

        // ============================================================
        // 3. ConcurrencyProperties（初始配置快照）
        // ============================================================
        properties = new ConcurrencyProperties();
        properties.setThreadPools(buildPoolConfig(ORDER, 4, 8, "60s", "AbortPolicy"));
        properties.getThreadPools().putAll(buildPoolConfig(NOTIFY, 2, 4, "30s", "DiscardPolicy"));

        // ============================================================
        // 4. 刷新器
        // ============================================================
        refresher = new ThreadPoolConfigRefresher(executors, binder, properties);
    }

    @AfterEach
    void tearDown() {
        orderExecutor.shutdownNow();
        notifyExecutor.shutdownNow();
    }

    // ============================================================================================
    // 事件过滤
    // ============================================================================================

    @Nested
    @DisplayName("事件过滤")
    class EventFiltering {

        @Test
        @DisplayName("忽略非 EnvironmentChangeEvent 的 ApplicationEvent")
        void ignoresNonEnvironmentChangeEvent() {
            // 执行前基线
            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(4);

            refresher.onApplicationEvent(new ApplicationEvent("test-source") {});

            // 没有任何参数变化
            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(4);
        }

        @Test
        @DisplayName("忽略不包含 thread-pools 前缀 key 的 EnvironmentChangeEvent")
        void ignoresNonMatchingKeys() {
            refresher.onApplicationEvent(new EnvironmentChangeEvent(
                    env, Set.of("spring.datasource.url", "server.port")));

            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(4);
            assertThat(notifyExecutor.getCorePoolSize()).isEqualTo(2);
        }
    }

    // ============================================================================================
    // 配置刷新
    // ============================================================================================

    @Nested
    @DisplayName("配置刷新")
    class Refresh {

        @Test
        @Timeout(5)
        @DisplayName("更新 corePoolSize 和 maximumPoolSize")
        void updatesCoreAndMax() {
            // 准备：修改 Environment 中的配置值
            updateProperty(key(ORDER, "core-pool-size"), "16");
            updateProperty(key(ORDER, "maximum-pool-size"), "32");

            // 触发刷新
            refresher.onApplicationEvent(environmentChangeEvent(
                    key(ORDER, "core-pool-size"), key(ORDER, "maximum-pool-size")));

            // 验证 order-executor 已刷新
            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(16);
            assertThat(orderExecutor.getMaximumPoolSize()).isEqualTo(32);
            // notification-executor 不受影响
            assertThat(notifyExecutor.getCorePoolSize()).isEqualTo(2);
        }

        @Test
        @Timeout(5)
        @DisplayName("更新 keepAliveTime 和 rejectedHandler")
        void updatesKeepAliveAndHandler() {
            // 修改配置
            updateProperty(key(ORDER, "keep-alive-time"), "120s");
            updateProperty(key(ORDER, "rejected-handler"), "CallerRunsPolicy");

            refresher.onApplicationEvent(environmentChangeEvent(
                    key(ORDER, "keep-alive-time"), key(ORDER, "rejected-handler")));

            assertThat(orderExecutor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(120);
            assertThat(orderExecutor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        }

        @Test
        @Timeout(5)
        @DisplayName("部分变更：仅更新变化字段，未变化字段保持不变")
        void partialChange_onlyChangedFieldsUpdated() {
            // 仅修改 corePoolSize
            updateProperty(key(ORDER, "core-pool-size"), "10");

            refresher.onApplicationEvent(environmentChangeEvent(key(ORDER, "core-pool-size")));

            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(10);
            // max 被自动提升以满足 core <= max 约束
            assertThat(orderExecutor.getMaximumPoolSize()).isEqualTo(10);
            assertThat(orderExecutor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(60); // 未变
        }

        @Test
        @Timeout(5)
        @DisplayName("配置值未变化时不下发 onResize")
        void unchangedConfig_noResize() {
            // 发送与原值相同的变更（虽然 key 变了但值相同）
            updateProperty(key(ORDER, "core-pool-size"), "4"); // 与原值相同

            refresher.onApplicationEvent(environmentChangeEvent(key(ORDER, "core-pool-size")));

            // 核心线程数应是原值 4
            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(4);
        }

        @Test
        @Timeout(5)
        @DisplayName("多个线程池同时变更")
        void multiplePoolsChanged() {
            // 修改两个池的不同参数
            updateProperty(key(ORDER, "core-pool-size"), "16");
            updateProperty(key(NOTIFY, "maximum-pool-size"), "10");

            refresher.onApplicationEvent(environmentChangeEvent(
                    key(ORDER, "core-pool-size"), key(NOTIFY, "maximum-pool-size")));

            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(16);
            assertThat(notifyExecutor.getMaximumPoolSize()).isEqualTo(10);
        }
    }

    // ============================================================================================
    // 边界情况
    // ============================================================================================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @Timeout(5)
        @DisplayName("变更引用的池名称没有对应的 DynamicExecutor Bean：不抛异常")
        void poolNameWithoutBean_noCrash() {
            // 新增一个池的配置，但没有对应的 DynamicExecutor
            updateProperty(key("unknown-pool", "core-pool-size"), "8");

            refresher.onApplicationEvent(environmentChangeEvent(key("unknown-pool", "core-pool-size")));

            // 不抛异常，仅日志告警
            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(4);
        }

        @Test
        @Timeout(5)
        @DisplayName("Environment 重新绑定失败时不改变已有快照")
        void bindFailure_keepsOldSnapshot() {
            // 清空环境使 Binder 无法重新绑定
            env.getPropertySources().remove("test");

            refresher.onApplicationEvent(environmentChangeEvent(key(ORDER, "core-pool-size")));

            // 线程池未被变更
            assertThat(orderExecutor.getCorePoolSize()).isEqualTo(4);
        }

        @Test
        @DisplayName("构造时 threadPools 为空不抛异常")
        void emptyThreadPoolsOnConstruction() {
            var empty = new ThreadPoolConfigRefresher(
                    Map.of(), binder, new ConcurrencyProperties());
            // 只验证构造不抛异常即可
            assertThat(empty).isNotNull();
        }
    }

    // ============================================================================================
    // 辅助方法
    // ============================================================================================

    private static String key(String poolName, String prop) {
        return PREFIX + "." + poolName + "." + prop;
    }

    private static Map<String, PoolProperties> buildPoolConfig(
            String name, int core, int max, String keepAlive, String handler) {

        var p = new PoolProperties();
        p.setCorePoolSize(core);
        p.setMaximumPoolSize(max);
        p.setKeepAliveTime(Duration.parse(keepAlive.startsWith("PT") ? keepAlive : "PT" + keepAlive.toUpperCase()));
        p.setRejectedHandler(handler);
        var map = new LinkedHashMap<String, PoolProperties>();
        map.put(name, p);
        return map;
    }

    private void updateProperty(String key, String value) {
        @SuppressWarnings("unchecked")
        var source = (Map<String, Object>) env.getPropertySources().get("test").getSource();
        source.put(key, value);
    }

    private static EnvironmentChangeEvent environmentChangeEvent(String... keys) {
        var set = new LinkedHashSet<String>();
        for (String k : keys) {
            set.add(k);
        }
        return new EnvironmentChangeEvent(new Object(), set);
    }
}
