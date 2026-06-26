package com.richie.component.tenant.strategy;

import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import org.apache.ibatis.plugin.Invocation;

/**
 * 多租户隔离策略接口。
 *
 * <p>每种隔离模式对应一个策略实现，在 SQL 执行前完成预处理
 * （如切换数据源、设置表后缀、切换 Schema 等）。</p>
 *
 * @author richie696
 * @since 2.0
 */
public interface TenancyStrategy {

    /**
     * 声明该策略是否支持指定的隔离模式。
     *
     * @param mode 隔离模式
     * @return 支持返回 {@code true}
     */
    boolean supports(IsolationMode mode);

    /**
     * SQL 执行前的预处理。
     *
     * @param invocation MyBatis 调用上下文
     * @param tenantInfo 当前租户的运行时信息
     */
    void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo);
}
