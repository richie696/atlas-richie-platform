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
package com.richie.component.web.jetty.management;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * Jetty {@link QueuedThreadPool} 运行时更新器。
 *
 * <p>不依赖任何配置中心。提供两种使用方式：</p>
 * <ol>
 *   <li><b>程序调用</b> — 消费方在 Nacos / Apollo / Spring Cloud Config 监听器中
 *       直接调用 {@link #refresh()} 或 {@link #refresh(int, int, Duration)} 即可
 *       触发线程池参数动态更新。</li>
 *   <li><b>自动刷新</b> — 当 classpath 中存在 Spring Cloud Context 时，
 *       {@code EnvironmentChangeEvent} 触发时会自动调用 {@link #refresh()}。
 *       组件本身无任何配置中心依赖。</li>
 * </ol>
 *
 * <p>可运行时更新的参数：</p>
 * <ul>
 *   <li>{@code maxThreads} — 最大线程数，立即生效</li>
 *   <li>{@code minThreads} — 最小线程数，立即生效</li>
 *   <li>{@code idleTimeout} — 空闲超时，下次回收时生效</li>
 * </ul>
 *
 * <p>注意：{@code acceptors} / {@code selectors} / {@code queueSize} 不可运行时更新，
 * 需重启后生效。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class JettyThreadPoolUpdater implements EnvironmentAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JettyThreadPoolUpdater.class);

    private static final String PREFIX = "server.jetty.threads";

    private final Server server;
    @Nullable
    private Environment environment;

    public JettyThreadPoolUpdater(Server server) {
        this.server = server;
    }

    @Override
    public void setEnvironment(@Nullable Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        // 启动时打印当前线程池配置，便于排查
        ThreadPool pool = server.getThreadPool();
        if (pool instanceof QueuedThreadPool qtp) {
            log.info("Jetty QueuedThreadPool initialized — config: max={} min={} idleTimeout={}ms  |  state: threads={} active={} idle={} queued={}",
                    qtp.getMaxThreads(), qtp.getMinThreads(), qtp.getIdleTimeout(),
                    qtp.getThreads(), qtp.getBusyThreads(), qtp.getIdleThreads(), qtp.getQueueSize());
        }
    }

    /**
     * 从当前 {@link Environment} 读取配置并应用到线程池。
     *
     * <p>消费方在配置中心回调中调用此方法。Environment 应当已被配置中心
     * 刷新（例如 Nacos 会在回调前注入新值）。</p>
     */
    public void refresh() {
        QueuedThreadPool qtp = resolvePool();
        if (qtp == null) return;

        int oldMax = qtp.getMaxThreads();
        int oldMin = qtp.getMinThreads();
        int oldIdle = qtp.getIdleTimeout();

        Integer newMaxVal = environment != null ? environment.getProperty(PREFIX + ".max", Integer.class) : null;
        Integer newMinVal = environment != null ? environment.getProperty(PREFIX + ".min", Integer.class) : null;
        int newMax = newMaxVal != null ? newMaxVal : oldMax;
        int newMin = newMinVal != null ? newMinVal : oldMin;
        Duration idleDuration = environment != null ? environment.getProperty(PREFIX + ".idle-timeout", Duration.class) : null;
        int newIdle = idleDuration != null ? (int) idleDuration.toMillis() : oldIdle;

        boolean changed = false;
        if (newMax != oldMax) {
            qtp.setMaxThreads(newMax);
            changed = true;
        }
        if (newMin != oldMin) {
            qtp.setMinThreads(newMin);
            changed = true;
        }
        if (idleDuration != null && newIdle != oldIdle) {
            qtp.setIdleTimeout(newIdle);
            changed = true;
        }

        if (changed) {
            log.info("Jetty thread pool refreshed — config: max={} min={} idleTimeout={}ms  |  state: threads={} active={} idle={} queued={}",
                    qtp.getMaxThreads(), qtp.getMinThreads(), qtp.getIdleTimeout(),
                    qtp.getThreads(), qtp.getBusyThreads(), qtp.getIdleThreads(), qtp.getQueueSize());
        } else {
            log.debug("Jetty thread pool refresh skipped: no config changes detected");
        }
    }

    /**
     * 使用显式参数更新线程池（跳过 Environment 读取）。
     *
     * @param maxThreads  新最大线程数（≤0 表示不修改）
     * @param minThreads  新最小线程数（≤0 表示不修改）
     * @param idleTimeout 新空闲超时（null 或负数表示不修改）
     */
    public void refresh(int maxThreads, int minThreads, @Nullable Duration idleTimeout) {
        QueuedThreadPool qtp = resolvePool();
        if (qtp == null) return;

        if (maxThreads > 0) qtp.setMaxThreads(maxThreads);
        if (minThreads > 0) qtp.setMinThreads(minThreads);
        if (idleTimeout != null && !idleTimeout.isNegative()) {
            qtp.setIdleTimeout((int) idleTimeout.toMillis());
        }

        log.info("Jetty thread pool explicitly updated — config: max={} min={} idleTimeout={}ms  |  state: threads={} active={} idle={} queued={}",
                qtp.getMaxThreads(), qtp.getMinThreads(), qtp.getIdleTimeout(),
                qtp.getThreads(), qtp.getBusyThreads(), qtp.getIdleThreads(), qtp.getQueueSize());
    }

    @Nullable
    private QueuedThreadPool resolvePool() {
        ThreadPool pool = server.getThreadPool();
        if (pool instanceof QueuedThreadPool qtp) {
            return qtp;
        }
        log.warn("Cannot refresh thread pool: pool type is {}",
                pool != null ? pool.getClass().getName() : "null");
        return null;
    }

}
