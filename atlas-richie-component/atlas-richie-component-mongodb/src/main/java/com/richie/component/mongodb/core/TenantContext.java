package com.richie.component.mongodb.core;

/**
 * 当前租户标识符的 ThreadLocal 持有器。
 * <p>
 * 此类提供租户上下文的静态 ThreadLocal 存储，支持 MongoDB 查询中的租户隔离。
 * 上下文应在任何数据库操作之前设置，并在操作完成后使用推荐模式清除：
 * <pre>
 * try {
 *     TenantContext.set(tenantId);
 *     // 执行租户作用域操作
 * } finally {
 *     TenantContext.clear();
 * }
 * </pre>
 * <p>
 * 当 {@link #get()} 返回 {@code null} 时，不应用租户过滤。
 * 当租户上下文为必需时，使用 {@link #require()}。
 *
 * @author Richie
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 设置当前线程的租户标识符。
     *
     * @param tenantId 要设置的租户标识符
     */
    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * 获取当前线程的租户标识符。
     *
     * @return 当前租户标识符，未设置则返回 {@code null}
     */
    public static String get() {
        return CURRENT_TENANT.get();
    }

    /**
     * 获取当前租户标识符，若未设置则抛出异常。
     *
     * @return 当前租户标识符
     * @throws IllegalStateException 如果未设置租户
     */
    public static String require() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext has not been set");
        }
        return tenantId;
    }

    /**
     * 清除当前线程的租户标识符。
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}