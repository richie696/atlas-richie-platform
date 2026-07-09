/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.concurrency.threadpool;

import java.util.Objects;

/**
 * 线程池运行态快照 —— {@link DynamicExecutor} 在某一时刻的状态描述。
 *
 * <p>该对象为不可变快照，供外部监控系统（Micrometer / Prometheus / 日志 / Actuator Endpoint）
 * 拉取使用。本组件不绑定任何监控框架，只负责暴露结构化的状态数据。</p>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * DynamicExecutor executor = ...;
 * PoolStatus status = executor.snapshot();
 *
 * // 接入 Micrometer
 * Gauge.builder("threadpool.active", status, PoolStatus::getActiveCount)
 *         .tag("pool", "order-service")
 *         .register(meterRegistry);
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class PoolStatus {

    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int poolSize;
    private final int activeCount;
    private final int queueSize;
    private final int queueRemainingCapacity;
    private final long completedTaskCount;
    private final long totalTaskCount;
    private final long rejectedCount;

    private PoolStatus(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maximumPoolSize = builder.maximumPoolSize;
        this.poolSize = builder.poolSize;
        this.activeCount = builder.activeCount;
        this.queueSize = builder.queueSize;
        this.queueRemainingCapacity = builder.queueRemainingCapacity;
        this.completedTaskCount = builder.completedTaskCount;
        this.totalTaskCount = builder.totalTaskCount;
        this.rejectedCount = builder.rejectedCount;
    }

    /**
     * 创建状态快照构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    // ── Getters ──────────────────────────────────────────────────────────────────

    /**
     * 核心线程数。
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 最大线程数。
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * 当前工作线程数（包含活跃与非活跃线程）。
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * 当前正在执行任务的线程数。
     */
    public int getActiveCount() {
        return activeCount;
    }

    /**
     * 队列中等待执行的任务数。
     */
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * 队列剩余容量。
     */
    public int getQueueRemainingCapacity() {
        return queueRemainingCapacity;
    }

    /**
     * 已完成的任务总数。
     */
    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    /**
     * 已提交的任务总数。
     */
    public long getTotalTaskCount() {
        return totalTaskCount;
    }

    /**
     * 累计被拒绝的任务数（自线程池创建以来）。
     */
    public long getRejectedCount() {
        return rejectedCount;
    }

    // ── Builder ──────────────────────────────────────────────────────────────────

    public static final class Builder {
        private int corePoolSize;
        private int maximumPoolSize;
        private int poolSize;
        private int activeCount;
        private int queueSize;
        private int queueRemainingCapacity;
        private long completedTaskCount;
        private long totalTaskCount;
        private long rejectedCount;

        private Builder() {
        }

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder poolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public Builder activeCount(int activeCount) {
            this.activeCount = activeCount;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder queueRemainingCapacity(int queueRemainingCapacity) {
            this.queueRemainingCapacity = queueRemainingCapacity;
            return this;
        }

        public Builder completedTaskCount(long completedTaskCount) {
            this.completedTaskCount = completedTaskCount;
            return this;
        }

        public Builder totalTaskCount(long totalTaskCount) {
            this.totalTaskCount = totalTaskCount;
            return this;
        }

        public Builder rejectedCount(long rejectedCount) {
            this.rejectedCount = rejectedCount;
            return this;
        }

        /**
         * 构建不可变的 {@link PoolStatus} 实例。
         *
         * @return 状态快照
         */
        public PoolStatus build() {
            return new PoolStatus(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PoolStatus that)) return false;
        return corePoolSize == that.corePoolSize
                && maximumPoolSize == that.maximumPoolSize
                && poolSize == that.poolSize
                && activeCount == that.activeCount
                && queueSize == that.queueSize
                && queueRemainingCapacity == that.queueRemainingCapacity
                && completedTaskCount == that.completedTaskCount
                && totalTaskCount == that.totalTaskCount
                && rejectedCount == that.rejectedCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(corePoolSize, maximumPoolSize, poolSize, activeCount,
                queueSize, queueRemainingCapacity, completedTaskCount, totalTaskCount, rejectedCount);
    }

    @Override
    public String toString() {
        return "PoolStatus{"
                + "corePoolSize=" + corePoolSize
                + ", maximumPoolSize=" + maximumPoolSize
                + ", poolSize=" + poolSize
                + ", activeCount=" + activeCount
                + ", queueSize=" + queueSize
                + ", queueRemainingCapacity=" + queueRemainingCapacity
                + ", completedTaskCount=" + completedTaskCount
                + ", totalTaskCount=" + totalTaskCount
                + ", rejectedCount=" + rejectedCount
                + '}';
    }
}
