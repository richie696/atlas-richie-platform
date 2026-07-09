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

    /**
     * 写入当前线程的数据源 key。
     *
     * <p>由 {@code DatabaseStrategy.beforeSqlExecute} 调用，
     * 后续 {@code DynamicTenantDataSource.determineCurrentLookupKey()} 读取此 key
     * 并从 HikariCP 数据源 Map 中路由到租户专属数据源。SQL 执行完毕后由
     * 连接清理拦截器自动调用 {@link #clear()}。</p>
     *
     * @param key 数据源 key（{@code "shared"} 或 {@code String.valueOf(tenantId)}）
     */
    public static void set(String key) {
        HOLDER.set(key);
    }

    /**
     * 读取当前线程的数据源 key。
     *
     * @return 数据源 key；未设置时返回 {@code null}
     */
    public static String get() {
        return HOLDER.get();
    }

    /**
     * 清理当前线程的数据源 key。
     *
     * <p>由 {@code ConnectionResetInterceptor} 在 SQL 执行完毕后调用，
     * 防止跨请求数据源路由泄漏（线程被连接池回收时残留旧 key）。</p>
     */
    public static void clear() {
        HOLDER.remove();
    }
}
