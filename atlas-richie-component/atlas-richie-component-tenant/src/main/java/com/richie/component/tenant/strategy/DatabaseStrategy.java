package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.DataSourceContextHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

/**
 * 数据库隔离策略（DATABASE 模式）。
 *
 * <p>将数据源 key 写入 {@link DataSourceContextHolder}，
 * 由 {@code DynamicTenantDataSource.determineCurrentLookupKey()} 路由到租户专属数据源。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class DatabaseStrategy extends AbstractTenancyStrategy {

    public DatabaseStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.DATABASE;
    }

    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        Long tenantId = TenantContext.getTenantId();
        validateTenantId(tenantId);

        String dataSourceKey = tenantInfo.getDataSourceName();
        if (dataSourceKey == null) {
            throw new IllegalArgumentException("No datasource configured for tenant " + tenantId);
        }
        DataSourceContextHolder.set(dataSourceKey);
    }
}
