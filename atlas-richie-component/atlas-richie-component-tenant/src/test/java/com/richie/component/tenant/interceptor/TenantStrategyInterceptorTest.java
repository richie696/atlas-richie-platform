package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.context.TransactionTenantHolder;
import com.richie.component.tenant.exception.DataSourceUnavailableException;
import com.richie.component.tenant.exception.TenantNotFoundException;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.component.tenant.strategy.TenancyStrategy;
import com.richie.component.tenant.strategy.TenancyStrategyFactory;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TenantStrategyInterceptor 单元测试。
 *
 * <p>覆盖场景：功能开关、无租户上下文、租户不存在、熔断检查（shared / tenant）、
 * 正常策略调度、异常记录 failure。</p>
 */
@ExtendWith(MockitoExtension.class)
class TenantStrategyInterceptorTest {

    @Mock
    private MultiTenancyProperties properties;

    @Mock
    private TenancyStrategyFactory strategyFactory;

    @Mock
    private TenantInfoProvider tenantInfoProvider;

    @Mock
    private DataSourceCircuitBreaker circuitBreaker;

    @Mock
    private Invocation invocation;

    @Mock
    private TenancyStrategy strategy;

    private TenantStrategyInterceptor interceptor;

    @BeforeAll
    static void initContext() {
        TenantContext.init(new ThreadLocalHolder());
    }

    @BeforeEach
    void setUp() {
        interceptor = new TenantStrategyInterceptor(
                properties, strategyFactory, tenantInfoProvider, circuitBreaker);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TransactionTenantHolder.clear();
    }

    // ==================== 功能开关 ====================

    @Test
    void intercept_whenDisabled_shouldProceedDirectly() throws Throwable {
        when(properties.isEnabled()).thenReturn(false);
        when(invocation.proceed()).thenReturn("result");

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo("result");
        verify(invocation).proceed();
        verifyNoInteractions(tenantInfoProvider, strategyFactory, circuitBreaker);
    }

    // ==================== 无租户上下文 ====================

    @Test
    void intercept_whenNoTenantContext_shouldProceedDirectly() throws Throwable {
        when(properties.isEnabled()).thenReturn(true);
        when(invocation.proceed()).thenReturn("result");

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo("result");
        verify(invocation).proceed();
        verifyNoInteractions(tenantInfoProvider);
    }

    @Test
    void intercept_whenTenantIdNull_shouldProceedDirectly() throws Throwable {
        when(properties.isEnabled()).thenReturn(true);
        when(invocation.proceed()).thenReturn("ok");

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(null);
        TenantContext.runWithTenant(principal, () -> {
            try {
                Object result = interceptor.intercept(invocation);
                assertThat(result).isEqualTo("ok");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });

        verify(invocation).proceed();
        verifyNoInteractions(tenantInfoProvider);
    }

    // ==================== 租户不存在 ====================

    @Test
    void intercept_whenTenantInfoNull_shouldThrowTenantNotFoundException() {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);
        when(tenantInfoProvider.getTenantInfo(1001L)).thenReturn(null);

        TenantContext.runWithTenant(principal, () -> {
            assertThatThrownBy(() -> interceptor.intercept(invocation))
                    .isInstanceOf(TenantNotFoundException.class)
                    .hasMessageContaining("1001");
        });
    }

    // ==================== 熔断检查 ====================

    @Test
    void intercept_whenSharedCircuitBreakerOpen_shouldThrowDataSourceUnavailable() {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);
        TenantInfo tenantInfo = buildTenantInfo(1001L, IsolationMode.COLUMN, null);

        when(tenantInfoProvider.getTenantInfo(1001L)).thenReturn(tenantInfo);
        when(circuitBreaker.isOpen("shared")).thenReturn(true);

        TenantContext.runWithTenant(principal, () -> {
            assertThatThrownBy(() -> interceptor.intercept(invocation))
                    .isInstanceOf(DataSourceUnavailableException.class)
                    .satisfies(ex -> assertThat(
                            ((DataSourceUnavailableException) ex).getDataSourceKey())
                            .isEqualTo("shared"));
        });
    }

    @Test
    void intercept_whenTenantDsCircuitBreakerOpen_shouldThrowDataSourceUnavailable() {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);
        TenantInfo tenantInfo = buildTenantInfo(1001L, IsolationMode.DATABASE, "tenant-1001-ds");

        when(tenantInfoProvider.getTenantInfo(1001L)).thenReturn(tenantInfo);
        when(circuitBreaker.isOpen("shared")).thenReturn(false);
        when(circuitBreaker.isOpen("tenant-1001-ds")).thenReturn(true);

        TenantContext.runWithTenant(principal, () -> {
            assertThatThrownBy(() -> interceptor.intercept(invocation))
                    .isInstanceOf(DataSourceUnavailableException.class)
                    .satisfies(ex -> assertThat(
                            ((DataSourceUnavailableException) ex).getDataSourceKey())
                            .isEqualTo("tenant-1001-ds"));
        });
    }

    // ==================== 正常策略调度 ====================

    @Test
    void intercept_normalFlow_columnMode_shouldDelegateStrategyAndRecordSuccess() throws Throwable {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);
        TenantInfo tenantInfo = buildTenantInfo(1001L, IsolationMode.COLUMN, null);

        when(tenantInfoProvider.getTenantInfo(1001L)).thenReturn(tenantInfo);
        when(circuitBreaker.isOpen("shared")).thenReturn(false);
        when(strategyFactory.getStrategy(IsolationMode.COLUMN)).thenReturn(strategy);
        when(invocation.proceed()).thenReturn("query-result");

        TenantContext.runWithTenant(principal, () -> {
            try {
                Object result = interceptor.intercept(invocation);
                assertThat(result).isEqualTo("query-result");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });

        verify(strategy).beforeSqlExecute(invocation, tenantInfo);
        verify(circuitBreaker).recordSuccess("shared");
        verify(circuitBreaker, never()).recordFailure(anyString());
    }

    @Test
    void intercept_normalFlow_databaseMode_shouldUseTenantDsKey() throws Throwable {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(2002L);
        TenantInfo tenantInfo = buildTenantInfo(2002L, IsolationMode.DATABASE, "ds_2002");

        when(tenantInfoProvider.getTenantInfo(2002L)).thenReturn(tenantInfo);
        when(circuitBreaker.isOpen("shared")).thenReturn(false);
        when(circuitBreaker.isOpen("ds_2002")).thenReturn(false);
        when(strategyFactory.getStrategy(IsolationMode.DATABASE)).thenReturn(strategy);
        when(invocation.proceed()).thenReturn("db-result");

        TenantContext.runWithTenant(principal, () -> {
            try {
                Object result = interceptor.intercept(invocation);
                assertThat(result).isEqualTo("db-result");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });

        verify(strategy).beforeSqlExecute(invocation, tenantInfo);
        verify(circuitBreaker).recordSuccess("ds_2002");
    }

    // ==================== 异常记录 failure ====================

    @Test
    void intercept_whenProceedThrows_shouldRecordFailureAndRethrow() {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);
        TenantInfo tenantInfo = buildTenantInfo(1001L, IsolationMode.COLUMN, null);

        when(tenantInfoProvider.getTenantInfo(1001L)).thenReturn(tenantInfo);
        when(circuitBreaker.isOpen("shared")).thenReturn(false);
        when(strategyFactory.getStrategy(IsolationMode.COLUMN)).thenReturn(strategy);

        RuntimeException sqlError = new RuntimeException("SQL execution error");
        try {
            when(invocation.proceed()).thenThrow(sqlError);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        TenantContext.runWithTenant(principal, () -> {
            assertThatThrownBy(() -> interceptor.intercept(invocation))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("SQL execution error");
        });

        verify(circuitBreaker).recordFailure("shared");
        verify(circuitBreaker, never()).recordSuccess(anyString());
    }

    @Test
    void intercept_whenProceedThrows_databaseMode_shouldRecordFailureOnTenantDs() {
        when(properties.isEnabled()).thenReturn(true);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(3003L);
        TenantInfo tenantInfo = buildTenantInfo(3003L, IsolationMode.DATABASE, "ds_3003");

        when(tenantInfoProvider.getTenantInfo(3003L)).thenReturn(tenantInfo);
        when(circuitBreaker.isOpen("shared")).thenReturn(false);
        when(circuitBreaker.isOpen("ds_3003")).thenReturn(false);
        when(strategyFactory.getStrategy(IsolationMode.DATABASE)).thenReturn(strategy);

        RuntimeException error = new RuntimeException("Connection lost");
        try {
            when(invocation.proceed()).thenThrow(error);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        TenantContext.runWithTenant(principal, () -> {
            assertThatThrownBy(() -> interceptor.intercept(invocation))
                    .hasMessage("Connection lost");
        });

        verify(circuitBreaker).recordFailure("ds_3003");
    }

    // ==================== 辅助方法 ====================

    private TenantInfo buildTenantInfo(Long tenantId, IsolationMode mode, String dsName) {
        return new TenantInfo()
                .setTenantId(tenantId)
                .setMode(mode)
                .setDataSourceName(dsName)
                .setStatus(TenantStatus.ACTIVE);
    }
}
