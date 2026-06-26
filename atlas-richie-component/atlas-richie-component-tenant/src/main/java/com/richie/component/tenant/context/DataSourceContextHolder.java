package com.richie.component.tenant.context;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.Nonnull;

/**
 * 数据源上下文持有器。
 *
 * <p>Database 模式下，由 {@code DatabaseStrategy} 在 SQL 执行前将数据源 key 写入，
 * {@code DynamicTenantDataSource.determineCurrentLookupKey()} 读取后路由到对应数据源。
 * SQL 执行完毕后由连接清理拦截器自动 clear。</p>
 *
 * <p>key 约定：{@code "shared"} 为共享数据源，{@code String.valueOf(tenantId)} 为租户独立数据源。</p>
 *
 * <p>通过 micrometer {@link ThreadLocalAccessor} 注册，支持跨异步边界自动传播。</p>
 *
 * @author richie696
 * @since 2.0
 */
public final class DataSourceContextHolder {

    private static final String CONTEXT_KEY = "tenant-datasource";
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
            @Nonnull @Override public Object key() { return CONTEXT_KEY; }
            @Override public String getValue() { return HOLDER.get(); }
            @Override public void setValue(@Nonnull String value) { HOLDER.set(value); }
            @Override public void setValue() { HOLDER.remove(); }
        });
    }

    private DataSourceContextHolder() {
    }

    public static void set(String key) {
        HOLDER.set(key);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
