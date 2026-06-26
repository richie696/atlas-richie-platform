package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.exception.BusinessException;
import com.richie.component.tenant.spi.TenantInfoProvider;

/**
 * 策略抽象基类，提供公共校验和辅助方法。
 *
 * @author richie696
 * @since 2.0
 */
public abstract class AbstractTenancyStrategy implements TenancyStrategy {

    protected final MultiTenancyProperties properties;
    protected final TenantInfoProvider tenantInfoProvider;

    protected AbstractTenancyStrategy(MultiTenancyProperties properties,
                                      TenantInfoProvider tenantInfoProvider) {
        this.properties = properties;
        this.tenantInfoProvider = tenantInfoProvider;
    }

    /**
     * 确保当前线程存在租户上下文。
     */
    protected void assertTenantPresent() {
        if (TenantContext.getTenantId() == null) {
            throw new BusinessException("Tenant not bound to current context");
        }
    }

    /**
     * 校验租户 ID 合法性（Long 类型仅做正整数校验）。
     */
    protected void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("Invalid tenant ID: " + tenantId);
        }
    }
}
