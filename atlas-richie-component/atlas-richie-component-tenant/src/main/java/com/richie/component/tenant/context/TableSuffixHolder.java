package com.richie.component.tenant.context;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.Nonnull;

/**
 * 表名后缀持有器。
 *
 * <p>Table 模式下，由 {@code TableStrategy} 在 SQL 执行前将租户的表后缀写入，
 * {@code DynamicTableNameInnerInterceptor} 读取后替换表名。</p>
 *
 * <p>例如：租户 1001 的后缀为 {@code "_1001"}，表 {@code t_order} 被替换为 {@code t_order_1001}。</p>
 *
 * <p>通过 micrometer {@link ThreadLocalAccessor} 注册，支持跨异步边界自动传播。</p>
 *
 * @author richie696
 * @since 2.0
 */
public final class TableSuffixHolder {

    private static final String CONTEXT_KEY = "tenant-table-suffix";
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
            @Nonnull @Override public Object key() { return CONTEXT_KEY; }
            @Override public String getValue() { return HOLDER.get(); }
            @Override public void setValue(@Nonnull String value) { HOLDER.set(value); }
            @Override public void setValue() { HOLDER.remove(); }
        });
    }

    private TableSuffixHolder() {
    }

    public static void set(String suffix) {
        HOLDER.set(suffix);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
