/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.actuator;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可选的拒绝任务计数器。
 *
 * <p>线程池拒绝次数不是 {@link ThreadPoolExecutor} 的原生属性，不能通过反射可靠推导。应用如果
 * 需要该指标，可把本处理器包装在原有拒绝策略外层；组件不会替换业务已有的拒绝策略。</p>
 */
public final class ObservabilityRejectedExecutionHandler implements RejectedExecutionHandler {

    private final RejectedExecutionHandler delegate;
    private final AtomicLong rejected = new AtomicLong();

    public ObservabilityRejectedExecutionHandler(RejectedExecutionHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejected.incrementAndGet();
        delegate.rejectedExecution(task, executor);
    }

    public long rejectedCount() {
        return rejected.get();
    }
}
