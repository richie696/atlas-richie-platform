package com.richie.component.concurrency.threadpool;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * 线程池热更新事件 —— 描述对 {@link DynamicExecutor} 进行运行时调整的目标参数。
 *
 * <p>事件源可以是配置中心、Admin API、定时器或 JMX 等任意外部机制。本组件只定义"要改成什么"，
 * 不关心事件从哪来，也不绑定任何配置中心依赖。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 外部发布事件（比如 Nacos/Etcd 配置变更监听器）
 * var event = PoolResizeEvent.builder()
 *         .corePoolSize(16)
 *         .maximumPoolSize(32)
 *         .keepAliveTime(Duration.ofSeconds(120))
 *         .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
 *         .build();
 *
 * // 内部订阅事件
 * executor.onResize(event);
 * }</pre>
 *
 * <h3>空字段语义</h3>
 * <p>{@code null} 表示"不调整"，{@link DynamicExecutor#onResize(PoolResizeEvent)}
 * 会跳过空字段，仅更新非空的配置项。</p>
 *
 * <h3>设计决策</h3>
 * <ul>
 *   <li><b>拒绝策略作为事件参数而非配置项</b>：拒绝策略本质上是一个运行时策略选择，
 *       项目上线初期配置不合理时，无需改代码重新发版即可热更新</li>
 *   <li><b>不包含 {@code queueCapacity}</b>：队列容量热更新无法解决生产者-消费者速率不匹配问题，
 *       反而会在实例宕机时积压更多未消费任务，扩大影响面</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class PoolResizeEvent {

    private final Integer corePoolSize;
    private final Integer maximumPoolSize;
    private final Duration keepAliveTime;
    private final RejectedExecutionHandler rejectedHandler;

    private PoolResizeEvent(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maximumPoolSize = builder.maximumPoolSize;
        this.keepAliveTime = builder.keepAliveTime;
        this.rejectedHandler = builder.rejectedHandler;
    }

    /**
     * 创建事件构建器。
     *
     * @return 新的事件构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    // ── Getters ──────────────────────────────────────────────────────────────────

    public Integer getCorePoolSize() {
        return corePoolSize;
    }

    public Integer getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    public RejectedExecutionHandler getRejectedHandler() {
        return rejectedHandler;
    }

    // ── Builder ──────────────────────────────────────────────────────────────────

    public static final class Builder {
        private Integer corePoolSize;
        private Integer maximumPoolSize;
        private Duration keepAliveTime;
        private RejectedExecutionHandler rejectedHandler;

        private Builder() {
        }

        /**
         * 设置核心线程数目标值。
         *
         * @param corePoolSize 新的核心线程数；{@code null} 表示不调整
         * @return this
         */
        public Builder corePoolSize(Integer corePoolSize) {
            if (corePoolSize != null && corePoolSize < 0) {
                throw new IllegalArgumentException("corePoolSize must be >= 0, got: " + corePoolSize);
            }
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * 设置最大线程数目标值。
         *
         * @param maximumPoolSize 新的最大线程数；{@code null} 表示不调整
         * @return this
         */
        public Builder maximumPoolSize(Integer maximumPoolSize) {
            if (maximumPoolSize != null && maximumPoolSize < 0) {
                throw new IllegalArgumentException("maximumPoolSize must be >= 0, got: " + maximumPoolSize);
            }
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        /**
         * 设置空闲线程存活时间。
         *
         * @param keepAliveTime 新的存活时间；{@code null} 或非正数表示不调整
         * @return this
         */
        public Builder keepAliveTime(Duration keepAliveTime) {
            if (keepAliveTime != null && (keepAliveTime.isNegative() || keepAliveTime.isZero())) {
                throw new IllegalArgumentException("keepAliveTime must be positive, got: " + keepAliveTime);
            }
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        /**
         * 设置拒绝策略。
         *
         * @param rejectedHandler 新的拒绝策略处理器；{@code null} 表示不调整
         * @return this
         */
        public Builder rejectedHandler(RejectedExecutionHandler rejectedHandler) {
            this.rejectedHandler = rejectedHandler;
            return this;
        }

        /**
         * 构建事件对象。
         *
         * @return 不可变的 {@link PoolResizeEvent} 实例
         */
        public PoolResizeEvent build() {
            return new PoolResizeEvent(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PoolResizeEvent that)) return false;
        return Objects.equals(corePoolSize, that.corePoolSize)
                && Objects.equals(maximumPoolSize, that.maximumPoolSize)
                && Objects.equals(keepAliveTime, that.keepAliveTime)
                && Objects.equals(rejectedHandler, that.rejectedHandler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler);
    }

    @Override
    public String toString() {
        return "PoolResizeEvent{"
                + "corePoolSize=" + corePoolSize
                + ", maximumPoolSize=" + maximumPoolSize
                + ", keepAliveTime=" + keepAliveTime
                + ", rejectedHandler=" + rejectedHandler
                + '}';
    }
}
