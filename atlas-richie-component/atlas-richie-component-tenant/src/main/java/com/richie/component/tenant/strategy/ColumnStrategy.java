package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

/**
 * 列隔离策略（COLUMN 模式）。
 *
 * <p>SQL 改写由 {@code TenantLineInnerInterceptor} 完成，此策略仅做前置校验。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class ColumnStrategy extends AbstractTenancyStrategy {

    public ColumnStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.COLUMN;
    }

    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        validateTenantId(TenantContext.getTenantId());
    }
}
