/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * <p><b>dataSourceKey 校验</b>：通过 {@link NamingConventionValidator} 校验为合法 SQL 标识符
 * （{@code ^[A-Za-z_][A-Za-z0-9_]*$}，1-128 字符），不合法时抛 {@link com.richie.component.tenant.exception.TenantErrorCode#TENANT_INVALID_NAMING}。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class DatabaseStrategy extends AbstractTenancyStrategy {

    /**
     * 构造 DATABASE 模式策略。
     *
     * @param properties         多租户配置
     * @param tenantInfoProvider 租户信息提供方
     */
    public DatabaseStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    /**
     * 仅匹配 {@link IsolationMode#DATABASE} 模式。
     *
     * @param mode 隔离模式
     * @return 是否由本策略处理
     */
    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.DATABASE;
    }

    /**
     * 校验租户上下文 + 将数据源 key 写入 {@link DataSourceContextHolder}，
     * 后续 {@code DynamicTenantDataSource.determineCurrentLookupKey()} 读取此 key
     * 并从 HikariCP 数据源 Map 中路由到租户专属数据源。
     *
     * @param invocation MyBatis 调用上下文（DATABASE 模式不读取）
     * @param tenantInfo 当前租户信息，用于读取 {@code dataSourceName}
     * @throws IllegalArgumentException                租户未配置数据源时
     * @throws com.richie.component.tenant.exception.BusinessException
     *         租户未绑定 / tenantId 非法 / dataSourceKey 不符合命名规范时
     */
    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        Long tenantId = TenantContext.getTenantId();
        validateTenantId(tenantId);

        String dataSourceKey = tenantInfo.getDataSourceName();
        if (dataSourceKey == null) {
            throw new IllegalArgumentException("No datasource configured for tenant " + tenantId);
        }
        NamingConventionValidator.validate(dataSourceKey, "dataSourceKey");
        DataSourceContextHolder.set(dataSourceKey);
    }
}