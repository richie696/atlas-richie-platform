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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

/**
 * 列隔离策略（COLUMN 模式，最简单）。
 *
 * <p>所有租户共享同一张表，通过 {@code tenant_id} 列区分。SQL 改写由
 * {@code TenantLineInnerInterceptor} 在 SQL 执行时自动追加 {@code tenant_id = ?} 条件
 * （SELECT/UPDATE/DELETE）或 {@code tenant_id} 列（INSERT），本策略不直接操作 SQL。</p>
 *
 * <p><b>前置校验</b>：仅校验租户上下文已绑定 + tenantId 合法，不做副作用操作。
 * 这是 5 种隔离模式中性能最高但隔离最弱的模式 — 适用于租户量不大（&lt;1000）、
 * 业务表天然含租户维度的场景。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class ColumnStrategy extends AbstractTenancyStrategy {

    /**
     * 构造 COLUMN 模式策略。
     *
     * @param properties         多租户配置（{@code multi-tenancy.*}）
     * @param tenantInfoProvider 租户信息提供方（查 {@code sys_tenant} 表）
     */
    public ColumnStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    /**
     * 仅匹配 {@link IsolationMode#COLUMN} 模式。
     *
     * @param mode 隔离模式
     * @return 是否由本策略处理
     */
    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.COLUMN;
    }

    /**
     * 前置校验：确保当前线程已绑定租户上下文 + tenantId 合法。
     *
     * <p>无副作用。SQL 改写由 {@code TenantLineInnerInterceptor} 在本方法之后执行。</p>
     *
     * @param invocation MyBatis 调用上下文（COLUMN 模式不读取）
     * @param tenantInfo 当前租户信息（COLUMN 模式不使用）
     * @throws com.richie.component.tenant.exception.BusinessException 租户未绑定或 tenantId 非法时
     */
    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        validateTenantId(TenantContext.getTenantId());
    }
}
