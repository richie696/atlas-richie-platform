package com.richie.component.tenant.cross;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.Nonnull;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring 线程池租户上下文装饰器。
 *
 * <p>基于 micrometer {@code context-propagation} 的 {@link ContextSnapshot}，
 * 在任务提交时捕获当前线程的所有已注册 {@code ThreadLocalAccessor} 值，
 * 在任务执行时恢复。覆盖租户上下文、数据源路由、表名后缀等全部上下文。</p>
 *
 * <p>自动覆盖 {@code @Async}、{@code CompletableFuture}、{@code @Scheduled}、虚拟线程等场景。</p>
 * <h2>使用方式</h2>
 * <pre>{@code
 * @Bean
 * public TaskExecutor taskExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(new TenantTaskDecorator());
 *     // ... 其他配置
 *     return executor;
 * }
 * }</pre>
 *
 * @author richie696
 * @since 2.0
 */
public class TenantTaskDecorator implements TaskDecorator {

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY =
            ContextSnapshotFactory.builder().build();

    @Nonnull
    @Override
    public Runnable decorate(@Nonnull Runnable runnable) {
        // 在提交线程中捕获所有已注册的 ThreadLocal 上下文快照
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        // 在执行线程中恢复上下文
        return snapshot.wrap(runnable);
    }
}
