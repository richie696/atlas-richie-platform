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
import com.richie.component.tenant.context.TableSuffixHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

/**
 * 表隔离策略（TABLE 模式）。
 *
 * <p>将租户的表后缀写入 {@link TableSuffixHolder}，
 * 由 {@code DynamicTableNameInnerInterceptor} 读取后替换表名（如 {@code products} → {@code products_1001}）。</p>
 *
 * <p><b>前置条件</b>：租户的 {@code tableSuffix} 必须通过白名单校验
 * （{@code ^[A-Za-z_][A-Za-z0-9_]*$}，1-128 字符），因为它会被直接拼接到 SQL 表名中。
 * 包含特殊字符（空格、分号、引号、连字符、Unicode 等）的后缀会导致 SQL 注入或语法错误。
 * 本策略通过 {@link NamingConventionValidator} 校验并抛 {@link TenantErrorCode#TENANT_INVALID_NAMING} 阻断。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TableStrategy extends AbstractTenancyStrategy {

    /**
     * 构造 TABLE 模式策略。
     *
     * @param properties         多租户配置
     * @param tenantInfoProvider 租户信息提供方
     */
    public TableStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    /**
     * 仅匹配 {@link IsolationMode#TABLE} 模式。
     *
     * @param mode 隔离模式
     * @return 是否由本策略处理
     */
    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.TABLE;
    }

    /**
     * 校验租户上下文 + 通过 {@link NamingConventionValidator} 校验表后缀
     * + 将后缀写入 {@link TableSuffixHolder}。
     *
     * <p>{@code DynamicTableNameInnerInterceptor} 读取后缀后将 SQL 表名替换为
     * {@code products_1001} 这种格式。</p>
     *
     * @param invocation MyBatis 调用上下文（TABLE 模式不读取）
     * @param tenantInfo 当前租户信息，用于读取 {@code tableSuffix}
     * @throws com.richie.component.tenant.exception.BusinessException
     *         租户未绑定 / tenantId 非法 / tableSuffix 不符合命名规范时
     */
    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        Long tenantId = TenantContext.getTenantId();
        validateTenantId(tenantId);

        String tableSuffix = tenantInfo.getTableSuffix();
        NamingConventionValidator.validate(tableSuffix, "tableSuffix");
        TableSuffixHolder.set(tableSuffix);
    }
}
