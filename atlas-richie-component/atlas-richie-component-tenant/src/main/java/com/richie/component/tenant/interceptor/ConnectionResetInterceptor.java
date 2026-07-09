/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.DataSourceContextHolder;
import com.richie.component.tenant.context.TableSuffixHolder;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * 连接重置拦截器。
 *
 * <p>在 MyBatis SQL 执行完毕后，清理与连接绑定的线程局部变量：
 * <ul>
 *   <li>{@link DataSourceContextHolder} — Database 模式的数据源 key</li>
 *   <li>{@link TableSuffixHolder} — Table 模式的表名后缀</li>
 * </ul>
 *
 * <p>确保连接归还连接池时不携带租户状态，避免连接复用导致的数据泄漏。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class ConnectionResetInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(ConnectionResetInterceptor.class);

    private final MultiTenancyProperties properties;

    /**
     * 构造连接重置拦截器。
     *
     * @param properties 多租户配置（{@code isEnabled()} 控制是否清理）
     */
    public ConnectionResetInterceptor(MultiTenancyProperties properties) {
        this.properties = properties;
    }

    /**
     * 拦截 MyBatis {@code StatementHandler.prepare(Connection, Integer)} 调用，
     * 在 {@code invocation.proceed()} 返回或抛异常后清理 {@link DataSourceContextHolder}
     * 和 {@link TableSuffixHolder}，防止连接复用时跨租户数据泄漏。
     *
     * <p>仅当 {@code multi-tenancy.enabled=true} 时执行清理，
     * 多租户组件整体禁用时（单租户模式）跳过清理逻辑以减少开销。</p>
     *
     * @param invocation MyBatis 调用上下文
     * @return {@code invocation.proceed()} 的返回值（由 MyBatis 决定类型）
     * @throws Throwable 透传 {@code proceed()} 的异常，清理逻辑在 finally 块保证执行
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } finally {
            if (properties.isEnabled()) {
                // SQL 执行完毕后清理连接级别的线程局部变量
                DataSourceContextHolder.clear();
                TableSuffixHolder.clear();
                if (log.isTraceEnabled()) {
                    log.trace("Connection context reset: DataSourceContextHolder + TableSuffixHolder cleared");
                }
            }
        }
    }
}
