package com.richie.component.tenant.reactive;

import com.richie.component.tenant.context.TenantContext;
import com.richie.contract.model.TenantPrincipal;
import reactor.core.publisher.Mono;

/**
 * Reactive 环境租户上下文门面。
 *
 * <p>在 WebFlux / Reactive 链路中，租户上下文存储在 Reactor {@code Context} 中
 * （通过 {@link TenantContextKeys#TENANT_KEY}），而非 ThreadLocal / ScopedValue。
 * 本门面提供 Mono 风格的 API 从 Reactor 上下文中读取租户信息。</p>
 *
 * <h2>与 {@link TenantContext} 的关系</h2>
 * <ul>
 *   <li>纯 Reactive 链路（如 WebFlux Controller → Service → R2DBC）→ 使用本门面</li>
 *   <li>阻塞链路（如 Servlet Filter → MyBatis 拦截器）→ 使用 {@link TenantContext}</li>
 *   <li>桥接场景（Reactive + 阻塞混用）→ TenantContextLifter 自动将 Reactor
 *       Context 中的租户恢复为 ThreadLocal / ScopedValue，反之亦然</li>
 * </ul>
 *
 * @author richie696
 * @since 2.1.0
 * @see TenantContextKeys
 * @see TenantContext
 */
public final class ReactorTenantContext {

    private ReactorTenantContext() {
    }

    /**
     * 从当前 Reactor {@code Context} 获取租户主体信息。
     *
     * @return 租户主体；未绑定时返回 {@link Mono#empty()}
     */
    public static Mono<TenantPrincipal> get() {
        return Mono.deferContextual(ctx -> {
            TenantPrincipal principal = ctx.getOrDefault(TenantContextKeys.TENANT_KEY, null);
            return principal != null ? Mono.just(principal) : Mono.empty();
        });
    }

    /**
     * 从当前 Reactor {@code Context} 获取租户 ID。
     *
     * @return 租户 ID；未绑定时返回 {@link Mono#empty()}
     */
    public static Mono<Long> getTenantId() {
        return get().map(TenantPrincipal::getTenantId);
    }

    /**
     * 将 {@link TenantPrincipal} 写入 Reactor {@code Context}。
     *
     * @param principal 租户主体
     * @return 写入后的 Context（用于 {@code Mono.contextWrite()}）
     */
    public static reactor.util.context.Context write(TenantPrincipal principal) {
        return reactor.util.context.Context.of(TenantContextKeys.TENANT_KEY, principal);
    }

    /**
     * 从 Reactor {@code Context} 中移除租户上下文。
     *
     * @return 移除后的 Context（用于 {@code Mono.contextWrite()}）
     */
    public static reactor.util.context.Context clear() {
        return reactor.util.context.Context.of(TenantContextKeys.TENANT_KEY, null);
    }

    /**
     * 将 Reactor {@code Context} 中的租户桥接到 {@link TenantContext}（ThreadLocal / ScopedValue）。
     *
     * <p>用于在 Reactive 链路中调用阻塞代码（如 MyBatis 查询）前恢复租户上下文，
     * 确保 {@link TenantContext#getTenantId()} 能正确读取当前租户。</p>
     *
     * <h4>用法</h4>
     * <pre>{@code
     * // 在 Reactive 链路的某一步需要调阻塞 API
     * return ReactorTenantContext.getTenantId()
     *     .flatMap(tenantId ->
     *         ReactorTenantContext.bridgeToBlocking(() ->
     *             blockingService.listByTenant(tenantId)  // 内部调 TenantContext.getTenantId()
     *         )
     *     );
     * }</pre>
     *
     * @param callable 需要在 {@link TenantContext} 上下文环境中执行的阻塞代码
     * @param <T>      返回值类型
     * @return 阻塞代码的执行结果（包装为 {@link Mono}）
     */
    public static <T> Mono<T> bridgeToBlocking(java.util.concurrent.Callable<T> callable) {
        return Mono.deferContextual(ctx -> {
            TenantPrincipal principal = ctx.getOrDefault(TenantContextKeys.TENANT_KEY, null);
            if (principal == null) {
                return Mono.fromCallable(callable);
            }
            return Mono.fromCallable(() ->
                TenantContext.runWithTenant(principal, (java.util.function.Supplier<T>) () -> {
                    try { return callable.call(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }));
        });
    }

    /**
     * 将 Reactor {@code Context} 中的租户桥接到 {@link TenantContext}（无返回值）。
     *
     * @param runnable 需要在 {@link TenantContext} 上下文环境中执行的阻塞代码
     * @return 阻塞代码的执行结果（包装为 {@link Mono}）
     */
    public static Mono<Void> bridgeToBlocking(Runnable runnable) {
        return Mono.deferContextual(ctx -> {
            TenantPrincipal principal = ctx.getOrDefault(TenantContextKeys.TENANT_KEY, null);
            if (principal == null) {
                return Mono.fromRunnable(runnable);
            }
            return Mono.fromRunnable(() ->
                TenantContext.runWithTenant(principal, runnable));
        });
    }
}
