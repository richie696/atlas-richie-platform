package com.richie.component.mongodb.core;

/**
 * Thread-local holder for the current tenant identifier.
 * <p>
 * This class provides a static ThreadLocal storage for tenant context, enabling
 * tenant isolation in MongoDB queries. The context should be set before any
 * database operation and cleared afterwards using the recommended pattern:
 * <pre>
 * try {
 *     TenantContext.set(tenantId);
 *     // perform tenant-scoped operations
 * } finally {
 *     TenantContext.clear();
 * }
 * </pre>
 * <p>
 * When {@link #get()} returns {@code null}, no tenant filtering is applied.
 * Use {@link #require()} when a tenant context is mandatory.
 *
 * @author Richie
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Sets the current tenant identifier for this thread.
     *
     * @param tenantId the tenant identifier to set
     */
    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Gets the current tenant identifier for this thread.
     *
     * @return the current tenant identifier, or {@code null} if not set
     */
    public static String get() {
        return CURRENT_TENANT.get();
    }

    /**
     * Gets the current tenant identifier, throwing if not set.
     *
     * @return the current tenant identifier
     * @throws IllegalStateException if no tenant is set
     */
    public static String require() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext has not been set");
        }
        return tenantId;
    }

    /**
     * Clears the current tenant identifier for this thread.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}