package com.richie.component.tenant.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.richie.contract.model.TenantPrincipal;

/**
 * 租户上下文持有器
 *
 * <p>使用 Alibaba {@link TransmittableThreadLocal} 实现，
 * 支持线程池（{@code @Async}、{@code CompletableFuture}）等异步场景下的自动传递。</p>
 *
 * <p>所有模块（MongoDB、MyBatis-Plus、MFA、Logging 等）统一通过此类获取当前租户，
 * 禁止各模块自行管理 ThreadLocal。</p>
 *
 * @author richie696
 * @since 1.0
 */
public final class TenantContextHolder {

    private static final TransmittableThreadLocal<TenantPrincipal> CTX = new TransmittableThreadLocal<>();

    private TenantContextHolder() {
    }

    /**
     * 设置当前线程的租户信息
     */
    public static void set(TenantPrincipal tenant) {
        CTX.set(tenant);
    }

    /**
     * 获取当前线程的租户信息
     *
     * @return 租户信息，可能为 {@code null}
     */
    public static TenantPrincipal get() {
        return CTX.get();
    }

    /**
     * 获取当前线程的租户 ID
     *
     * @return 租户 ID，可能为 {@code null}
     */
    public static Long getTenantId() {
        TenantPrincipal tenant = CTX.get();
        return tenant != null ? tenant.getTenantId() : null;
    }

    /**
     * 获取当前线程的租户展示名称
     *
     * @return 租户名称，可能为 {@code null}
     */
    public static String getTenantName() {
        TenantPrincipal tenant = CTX.get();
        return tenant != null ? tenant.getTenantName() : null;
    }

    /**
     * 获取当前线程的租户信息，不允许为空
     *
     * @return 租户信息
     * @throws IllegalStateException 如果未设置租户上下文
     */
    public static TenantPrincipal require() {
        TenantPrincipal tenant = CTX.get();
        if (tenant == null) {
            throw new IllegalStateException("未设置租户上下文，请确认请求经过 TenantFilter/TenantContextInitializer");
        }
        return tenant;
    }

    /**
     * 获取当前线程的租户 ID，不允许为空
     *
     * @return 租户 ID
     * @throws IllegalStateException 如果未设置租户上下文
     */
    public static Long requireTenantId() {
        TenantPrincipal tenant = require();
        return tenant.getTenantId();
    }

    /**
     * 清理当前线程的租户上下文
     *
     * <p>务必在请求结束时调用，防止内存泄漏。</p>
     */
    public static void clear() {
        CTX.remove();
    }
}
