/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /**
     * 写入当前线程的表名后缀。
     *
     * <p>由 {@code TableStrategy.beforeSqlExecute} 调用。
     * {@code DynamicTableNameInnerInterceptor} 读取后缀后将 SQL 中的
     * {@code t_order} 替换为 {@code t_order_1001}（租户 1001 的后缀示例）。</p>
     *
     * @param suffix 表名后缀（已通过 {@code NamingConventionValidator} 校验）
     */
    public static void set(String suffix) {
        HOLDER.set(suffix);
    }

    /**
     * 读取当前线程的表名后缀。
     *
     * @return 表名后缀；未设置时返回 {@code null}
     */
    public static String get() {
        return HOLDER.get();
    }

    /**
     * 清理当前线程的表名后缀。
     *
     * <p>由 {@code ConnectionResetInterceptor} 在 SQL 执行完毕后调用，
     * 防止跨请求表名替换泄漏。</p>
     */
    public static void clear() {
        HOLDER.remove();
    }
}
