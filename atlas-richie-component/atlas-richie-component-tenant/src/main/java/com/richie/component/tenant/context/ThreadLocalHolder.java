package com.richie.component.tenant.context;

import com.richie.contract.model.TenantPrincipal;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * 基于 plain {@link ThreadLocal} 的租户上下文持有器（降级实现）。
 *
 * <p>当 {@code multi-tenancy.force-thread-local=true} 或 JVM 不支持 {@code ScopedValue} 时使用。
 * 通过 micrometer {@code context-propagation} 注册 {@link ThreadLocalAccessor}，
 * 自动覆盖 {@code @Async}、{@code CompletableFuture}、{@code @Scheduled}、虚拟线程等异步场景。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class ThreadLocalHolder implements TenantContextHolder {

    static final String CONTEXT_KEY = "tenant-principal";

    private static final ThreadLocal<TenantPrincipal> CURRENT_TENANT = new ThreadLocal<>();

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new Accessor());
    }

    @Override
    public void runWithTenant(TenantPrincipal principal, Runnable task) {
        TenantPrincipal previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(principal);
        try {
            task.run();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    @Override
    public <T> T runWithTenant(TenantPrincipal principal, Supplier<T> task) {
        TenantPrincipal previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(principal);
        try {
            return task.get();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    @Override
    public TenantPrincipal get() {
        return CURRENT_TENANT.get();
    }

    @Override
    public void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * 直接设置当前线程的租户上下文（测试辅助方法）。
     *
     * <p>仅供单元测试使用。生产代码应始终通过 {@code runWithTenant} 管理上下文生命周期。</p>
     *
     * @param principal 租户主体信息
     */
    public void set(TenantPrincipal principal) {
        CURRENT_TENANT.set(principal);
    }

    /**
     * micrometer {@link ThreadLocalAccessor} 实现，使租户上下文可跨异步边界自动传播。
     *
     * <p>覆盖场景：{@code @Async}、{@code CompletableFuture}、{@code @Scheduled}、虚拟线程等。</p>
     */
    static class Accessor implements ThreadLocalAccessor<TenantPrincipal> {

        @Nonnull
        @Override
        public Object key() {
            return CONTEXT_KEY;
        }

        @Override
        public TenantPrincipal getValue() {
            return CURRENT_TENANT.get();
        }

        @Override
        public void setValue(@Nonnull TenantPrincipal value) {
            CURRENT_TENANT.set(value);
        }

        @Override
        public void setValue() {
            CURRENT_TENANT.remove();
        }
    }
}
