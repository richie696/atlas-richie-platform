package com.richie.component.tenant.reactive;

import com.richie.contract.model.TenantPrincipal;
import reactor.util.context.Context;

/**
 * Reactor {@link Context} 的租户上下文 Key 常量。
 *
 * <p>所有跨 Reactive 算子链的租户上下文传递均通过此 Key 存储在
 * Reactor {@link Context} 中，避免依赖 ThreadLocal / ScopedValue
 * 在响应式环境下不可靠的问题。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 写入（通常在 TenantWebFilter 或入口拦截器）
 * Mono.deferContextual(ctx -> {
 *     // 已有 ctx.get(TenantContextKeys.TENANT_KEY)
 *     ...
 * });
 *
 * // 读取（业务层统一通过 ReactorTenantContext 门面）
 * ReactorTenantContext.getTenantId()
 *     .flatMap(tenantId -> orderService.list(tenantId));
 * }</pre>
 *
 * @author richie696
 * @since 2.1.0
 */
public final class TenantContextKeys {

    private TenantContextKeys() {
    }

    /**
     * Reactor {@link Context} 中存储 {@link TenantPrincipal} 的 Key。
     */
    public static final Class<TenantPrincipal> TENANT_KEY = TenantPrincipal.class;

}
