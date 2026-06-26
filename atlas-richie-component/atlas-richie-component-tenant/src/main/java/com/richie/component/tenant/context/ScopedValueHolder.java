package com.richie.component.tenant.context;

import com.richie.contract.model.TenantPrincipal;

import java.util.function.Supplier;

/**
 * 基于 JDK {@code ScopedValue} 的租户上下文持有器（首选实现）。
 *
 * <p>虚拟线程友好：{@code ScopedValue} 在作用域范围内对所有子线程可见，
 * 无需 {@code TransmittableThreadLocal} Agent 字节码增强，
 * 且在 10 万级虚拟线程场景下无内存副本开销。</p>
 *
 * <p>唯一的限制：子线程的生命周期不能超出 {@code runWithTenant} 作用域，
 * 否则 {@code ScopedValue} 不可见（需使用 {@code TenantTaskDecorator} 显式传递）。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class ScopedValueHolder implements TenantContextHolder {

    private static final ScopedValue<TenantPrincipal> CURRENT_TENANT = ScopedValue.newInstance();

    @Override
    public void runWithTenant(TenantPrincipal principal, Runnable task) {
        ScopedValue.where(CURRENT_TENANT, principal).run(task);
    }

    @Override
    public <T> T runWithTenant(TenantPrincipal principal, Supplier<T> task) {
        return ScopedValue.where(CURRENT_TENANT, principal).call(task::get);
    }

    @Override
    public TenantPrincipal get() {
        return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : null;
    }

    @Override
    public void clear() {
        // ScopedValue 作用域结束自动失效，无需手动清理
    }
}
