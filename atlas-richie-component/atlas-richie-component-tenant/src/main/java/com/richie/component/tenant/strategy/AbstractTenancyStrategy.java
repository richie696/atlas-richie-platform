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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.contract.exception.BusinessException;
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
     * 确保当前线程已绑定租户上下文。
     *
     * <p>未绑定时抛 {@link BusinessException}（消息：
     * {@code "Tenant not bound to current context"}），由调用方捕获后转换为
     * 401/403 错误响应或日志告警。</p>
     */
    protected void assertTenantPresent() {
        if (TenantContext.getTenantId() == null) {
            throw new BusinessException("TENANT_NOT_BOUND", "Tenant not bound to current context");
        }
    }

    /**
     * 校验租户 ID 合法性。仅接受 {@code null} 以外的正整数（{@code > 0}），
     * 拒绝 null / 0 / 负数。校验失败抛 {@link BusinessException}。
     *
     * <p>不校验租户是否存在（{@code sys_tenant} 表中是否注册）— 这是
     * {@code TenantIdentityFilter} 的职责，本方法只做格式校验。</p>
     *
     * @param tenantId 租户 ID
     * @throws BusinessException tenantId 为 null 或非正整数时
     */
    protected void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("INVALID_TENANT_ID", "Invalid tenant ID: " + tenantId);
        }
    }
}
