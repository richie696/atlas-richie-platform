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

    public ConnectionResetInterceptor(MultiTenancyProperties properties) {
        this.properties = properties;
    }

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
