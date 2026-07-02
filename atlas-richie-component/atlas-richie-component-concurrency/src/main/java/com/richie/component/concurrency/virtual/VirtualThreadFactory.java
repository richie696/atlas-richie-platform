package com.richie.component.concurrency.virtual;

import jakarta.annotation.Nonnull;

import java.lang.ScopedValue.Carrier;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 虚拟线程工厂 —— 创建带命名规则的可观测虚拟线程，并支持 {@link ScopedValue} 上下文绑定。
 *
 * <h3>基本用法</h3>
 * <pre>{@code
 * // 创建带前缀的虚拟线程
 * var factory = VirtualThreadFactory.of("async-job");
 * Thread vt = factory.newThread(() -> process());
 * vt.start();
 *
 * // 自定义构建
 * var factory = VirtualThreadFactory.builder()
 *         .namePrefix("scoped-task")
 *         .scopedValue(SCOPE_TOKEN, "my-token")
 *         .build();
 * }</pre>
 *
 * <h3>结合 Spring {@code @Async}</h3>
 * <pre>{@code
 * @Bean
 * public ThreadPoolTaskExecutor customAsyncExecutor() {
 *     var executor = new ThreadPoolTaskExecutor();
 *     executor.setThreadFactory(VirtualThreadFactory.of("async-"));
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class VirtualThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicLong threadNumber = new AtomicLong(1);
    private final ScopedValueBinding<?>[] scopedValueBindings;

    private VirtualThreadFactory(String namePrefix, ScopedValueBinding<?>[] scopedValueBindings) {
        this.namePrefix = namePrefix;
        this.scopedValueBindings = scopedValueBindings;
    }

    /**
     * 创建一个新的虚拟线程。
     *
     * @param task 要运行的任务
     * @return 未启动的虚拟线程
     */
    @Override
    public Thread newThread(@Nonnull Runnable task) {
        var name = namePrefix + threadNumber.getAndIncrement();
        Thread thread;

        if (scopedValueBindings.length > 0) {
            thread = Thread.ofVirtual()
                    .name(name)
                    .unstarted(bindScopedValues(task));
        } else {
            thread = Thread.ofVirtual()
                    .name(name)
                    .unstarted(task);
        }

        return thread;
    }

    /**
     * 为任务绑定所有已注册的 ScopedValue，返回包装后的 Runnable。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Runnable bindScopedValues(Runnable task) {
        Carrier carrier = ScopedValue.where(
                (ScopedValue) scopedValueBindings[0].key(),
                scopedValueBindings[0].value());
        for (int i = 1; i < scopedValueBindings.length; i++) {
            carrier = carrier.where(
                    (ScopedValue) scopedValueBindings[i].key(),
                    scopedValueBindings[i].value());
        }
        final Carrier captured = carrier;
        return () -> captured.run(task);
    }

    /**
     * 创建 {@link Builder} 实例。
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建一个仅指定名称前缀的简单虚拟线程工厂。
     *
     * @param namePrefix 线程名前缀
     * @return VirtualThreadFactory 实例
     * @throws NullPointerException 如果 {@code namePrefix} 为 null
     */
    public static VirtualThreadFactory of(String namePrefix) {
        Objects.requireNonNull(namePrefix, "namePrefix must not be null");
        return builder().namePrefix(namePrefix).build();
    }

    // ---- inner types ----

    /**
     * {@link VirtualThreadFactory} 构建器。
     */
    public static final class Builder {
        private String namePrefix = "vt-";
        private ScopedValueBinding<?>[] scopedValueBindings = new ScopedValueBinding[0];

        private Builder() {
        }

        /**
         * 设置虚拟线程名称前缀（默认：{@code "vt-"}）。
         *
         * @param namePrefix 线程名前缀
         * @return 当前构建器
         * @throws NullPointerException 如果 {@code namePrefix} 为 null
         */
        public Builder namePrefix(String namePrefix) {
            Objects.requireNonNull(namePrefix, "namePrefix must not be null");
            this.namePrefix = namePrefix;
            return this;
        }

        /**
         * 绑定一个 {@link ScopedValue}，使工厂创建的每个虚拟线程都携带该上下文。
         *
         * @param <T>   值类型
         * @param key   ScopedValue 键
         * @param value 要绑定的值
         * @return 当前构建器
         */
        public <T> Builder scopedValue(ScopedValue<T> key, T value) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            var binding = new ScopedValueBinding(key, value);
            var newBindings = new ScopedValueBinding[scopedValueBindings.length + 1];
            System.arraycopy(scopedValueBindings, 0, newBindings, 0, scopedValueBindings.length);
            newBindings[scopedValueBindings.length] = binding;
            this.scopedValueBindings = newBindings;
            return this;
        }

        /**
         * 绑定多个 {@link ScopedValue}。
         *
         * @param bindings ScopedValue 绑定数组
         * @return 当前构建器
         */
        public Builder scopedValues(ScopedValueBinding<?>... bindings) {
            this.scopedValueBindings = bindings.clone();
            return this;
        }

        /**
         * 构建 {@link VirtualThreadFactory} 实例。
         *
         * @return 新的 VirtualThreadFactory
         */
        public VirtualThreadFactory build() {
            return new VirtualThreadFactory(namePrefix, scopedValueBindings);
        }
    }

    /**
     * {@link ScopedValue} 键值对绑定，用于在虚拟线程创建时绑定上下文。
     *
     * @param <T> 值类型
     */
    public record ScopedValueBinding<T>(ScopedValue<T> key, T value) {
    }
}
