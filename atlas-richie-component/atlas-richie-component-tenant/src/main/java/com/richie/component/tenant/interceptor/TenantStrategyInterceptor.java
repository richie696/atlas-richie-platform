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

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.TransactionTenantHolder;
import com.richie.component.tenant.exception.DataSourceUnavailableException;
import com.richie.component.tenant.exception.TenantNotFoundException;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.component.tenant.strategy.TenancyStrategy;
import com.richie.component.tenant.strategy.TenancyStrategyFactory;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * 策略调度拦截器。
 *
 * <p>在 MyBatis SQL 执行前，根据当前租户的隔离模式调用对应策略的
 * {@link TenancyStrategy#beforeSqlExecute}，完成数据源路由、表后缀设置、
 * Schema 切换等预处理。</p>
 *
 * <p>同时执行熔断检查：如果当前租户的数据源（或 shared 数据源）处于熔断状态，
 * 则拒绝访问并抛出 {@link DataSourceUnavailableException}。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class TenantStrategyInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantStrategyInterceptor.class);

    private static final String SHARED_DS_KEY = "shared";

    private final MultiTenancyProperties properties;
    private final TenancyStrategyFactory strategyFactory;
    private final TenantInfoProvider tenantInfoProvider;
    private final DataSourceCircuitBreaker circuitBreaker;

    /**
     * 构造策略调度拦截器。
     *
     * @param properties         多租户配置
     * @param strategyFactory    隔离模式 → 策略实现工厂
     * @param tenantInfoProvider 租户信息提供方（查 {@code sys_tenant} 表）
     * @param circuitBreaker     数据源熔断器（共享 + 租户独立）
     */
    public TenantStrategyInterceptor(MultiTenancyProperties properties,
                                     TenancyStrategyFactory strategyFactory,
                                     TenantInfoProvider tenantInfoProvider,
                                     DataSourceCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.strategyFactory = strategyFactory;
        this.tenantInfoProvider = tenantInfoProvider;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 拦截 MyBatis {@code StatementHandler.prepare(Connection, Integer)} 调用,完成：
     * <ol>
     *   <li>熔断检查（shared + 租户独立数据源）</li>
     *   <li>查租户信息（缺失时抛 {@link TenantNotFoundException}）</li>
     *   <li>按 {@code tenantInfo.mode} 委派给 4 个 base 策略之一</li>
     *   <li>执行 SQL 并按结果记录熔断器成功/失败</li>
     * </ol>
     *
     * <p>早返回条件：{@code multi-tenancy.enabled=false} 或租户上下文未绑定时,直接
     * 透传 MyBatis 调用不做任何租户处理（单租户兼容）。</p>
     *
     * @param invocation MyBatis 调用上下文
     * @return {@code invocation.proceed()} 的返回值
     * @throws Throwable 透传 SQL 执行异常 + 业务异常（{@link TenantNotFoundException} /
     *                   {@link DataSourceUnavailableException} / 策略内部异常）
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }

        TenantPrincipal principal = TenantContext.get();
        if (principal == null || principal.getTenantId() == null) {
            return invocation.proceed();
        }

        Long tenantId = principal.getTenantId();
        TenantInfo tenantInfo = tenantInfoProvider.getTenantInfo(tenantId);
        if (tenantInfo == null) {
            throw new TenantNotFoundException(tenantId);
        }

        // 冻结事务内租户 ID — 防 runWithTenant(tenantB) 跨租户写入
        // 该检测由 TenantContext.checkTransactionTenantSwitch() 调 TransactionTenantHolder.checkSwitch() 完成
        TransactionTenantHolder.freeze(tenantId);

        // 熔断检查
        checkCircuitBreaker(tenantInfo);

        // 策略调度
        TenancyStrategy strategy = strategyFactory.getStrategy(tenantInfo.getMode());
        strategy.beforeSqlExecute(invocation, tenantInfo);

        // 执行 SQL
        Object result;
        try {
            result = invocation.proceed();
            // 记录成功
            String dsKey = resolveDataSourceKey(tenantInfo);
            circuitBreaker.recordSuccess(dsKey);
        } catch (Throwable t) {
            // 记录失败
            String dsKey = resolveDataSourceKey(tenantInfo);
            circuitBreaker.recordFailure(dsKey);
            throw t;
        } finally {
            // 清除事务租户冻结 — 同事务下次 SQL 重新冻结(若上下文未变则冻结值不变)
            TransactionTenantHolder.clear();
        }

        return result;
    }

    /**
     * 检查数据源熔断状态。
     */
    private void checkCircuitBreaker(TenantInfo tenantInfo) {
        // 检查 shared 数据源（Column / Table / Schema 模式共用）
        if (circuitBreaker.isOpen(SHARED_DS_KEY)) {
            throw new DataSourceUnavailableException(SHARED_DS_KEY,
                "Shared data source is currently unavailable (circuit breaker OPEN)");
        }

        // 检查租户独立数据源（Database 模式）
        String dsKey = resolveDataSourceKey(tenantInfo);
        if (!SHARED_DS_KEY.equals(dsKey) && circuitBreaker.isOpen(dsKey)) {
            throw new DataSourceUnavailableException(dsKey,
                "Tenant data source is currently unavailable: " + dsKey);
        }
    }

    /**
     * 解析租户对应的数据源 key。
     */
    private String resolveDataSourceKey(TenantInfo tenantInfo) {
        return tenantInfo.getDataSourceName() != null
            ? tenantInfo.getDataSourceName()
            : SHARED_DS_KEY;
    }
}
