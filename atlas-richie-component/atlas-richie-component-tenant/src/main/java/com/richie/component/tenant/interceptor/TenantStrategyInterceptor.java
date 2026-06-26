package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
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

    public TenantStrategyInterceptor(MultiTenancyProperties properties,
                                     TenancyStrategyFactory strategyFactory,
                                     TenantInfoProvider tenantInfoProvider,
                                     DataSourceCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.strategyFactory = strategyFactory;
        this.tenantInfoProvider = tenantInfoProvider;
        this.circuitBreaker = circuitBreaker;
    }

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
