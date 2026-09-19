/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.actuator;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 为应用显式声明的线程池补充低基数运行指标。
 */
public final class ExecutorMetricsBinder implements MeterBinder {

    private final Map<String, Executor> executors;
    private final ObservabilityState state;

    public ExecutorMetricsBinder(Map<String, Executor> executors, ObservabilityState state) {
        this.executors = Map.copyOf(Objects.requireNonNull(executors, "executors"));
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (registry == null || state.disabled()) {
            return;
        }
        executors.forEach((name, executor) -> {
            ThreadPoolExecutor threadPool = resolve(executor);
            if (threadPool == null) {
                return;
            }
            Gauge.builder("executor.active", threadPool, value -> value.getActiveCount())
                    .tag("pool_name", name)
                    .description("Currently active executor threads")
                    .register(registry);
            Gauge.builder("executor.pool.size", threadPool, value -> value.getPoolSize())
                    .tag("pool_name", name)
                    .register(registry);
            Gauge.builder("executor.pool.max", threadPool, value -> value.getMaximumPoolSize())
                    .tag("pool_name", name)
                    .register(registry);
            Gauge.builder("executor.queue.size", threadPool, value -> value.getQueue().size())
                    .tag("pool_name", name)
                    .register(registry);
            Gauge.builder("executor.completed", threadPool, value -> value.getCompletedTaskCount())
                    .tag("pool_name", name)
                    .register(registry);
            RejectedExecutionHandler rejectionHandler = threadPool.getRejectedExecutionHandler();
            if (rejectionHandler instanceof ObservabilityRejectedExecutionHandler observableHandler) {
                Gauge.builder("executor.rejected", observableHandler,
                                ObservabilityRejectedExecutionHandler::rejectedCount)
                        .tag("pool_name", name)
                        .description("Rejected executor tasks")
                        .register(registry);
            }
        });
    }

    private static ThreadPoolExecutor resolve(Executor executor) {
        if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor;
        }
        if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
            try {
                return taskExecutor.getThreadPoolExecutor();
            } catch (IllegalStateException ignored) {
                return null;
            }
        }
        return null;
    }
}
