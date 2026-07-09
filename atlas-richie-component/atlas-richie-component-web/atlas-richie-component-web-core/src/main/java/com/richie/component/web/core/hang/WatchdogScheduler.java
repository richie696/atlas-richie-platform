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
package com.richie.component.web.core.hang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hang 看门狗调度器（README.md §4.4）。
 * <p>
 * 每个请求注册一个 {@link ScheduledFuture}，在阈值时间后触发回调（dump 线程栈 / publish HangEvent）。
 * 请求正常完成时 {@link WatchHandle#cancel} 取消未来触发。
 *
 * <h2>线程模型</h2>
 * <ul>
 *   <li>默认共享 {@link ScheduledExecutorService} 单例（{@link #getDefault()}）——3 线程守护线程池</li>
 *   <li>shutdown 由 Spring 生命周期管理（{@link HangAutoConfiguration#watchdogScheduler()}
 *       声明 {@code destroyMethod = "shutdown"}）——应用关闭时自动触发</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public class WatchdogScheduler {

    private static final Logger log = LoggerFactory.getLogger(WatchdogScheduler.class);

    private static final AtomicLong ID_GEN = new AtomicLong();

    private static volatile WatchdogScheduler DEFAULT;

    private final ScheduledExecutorService executor;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public WatchdogScheduler(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public static WatchdogScheduler getDefault() {
        WatchdogScheduler local = DEFAULT;
        if (local == null) {
            synchronized (WatchdogScheduler.class) {
                local = DEFAULT;
                if (local == null) {
                    local = new WatchdogScheduler(
                            Executors.newScheduledThreadPool(3, r -> {
                                Thread t = new Thread(r, "web-hang-watchdog-" + ID_GEN.incrementAndGet());
                                t.setDaemon(true);
                                return t;
                            }));
                    DEFAULT = local;
                }
            }
        }
        return local;
    }

    /**
     * 关闭底层 {@link ScheduledExecutorService}（幂等）。由 Spring 在 bean 销毁阶段自动调用
     * （{@code @Bean(destroyMethod = "shutdown")}），非 Spring 用法也安全（多次调用仅首次生效）。
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("WatchdogScheduler shutdown complete");
    }

    /**
     * 注册一个 watchdog。{@code onTimeout} 在阈值后被调用（请求未完成时）。
     *
     * @param thresholdMillis 阈值（毫秒）
     * @param onTimeout       触发回调
     * @return handle 用于取消
     */
    public WatchHandle schedule(long thresholdMillis, Runnable onTimeout) {
        ScheduledFuture<?> future = executor.schedule(onTimeout, thresholdMillis, TimeUnit.MILLISECONDS);
        return new WatchHandle(future);
    }

    /**
     * 反注册 handle（看门狗任务的取消 token）。
     */
    public static final class WatchHandle {
        private final ScheduledFuture<?> future;
        private volatile boolean cancelled;

        WatchHandle(ScheduledFuture<?> future) {
            this.future = future;
        }

        public boolean cancel() {
            if (cancelled) {
                return false;
            }
            cancelled = true;
            boolean ok = future.cancel(false);
            log.trace("Watchdog cancelled: ok={}", ok);
            return ok;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}