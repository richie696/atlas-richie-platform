package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.exception.BusinessException;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

/**
 * 混合隔离策略（HYBRID 模式）。
 *
 * <p>按租户的固定隔离模式委托给对应的策略实现。
 * 每个租户的 {@code TenantInfo.mode} 在一个发布周期内保持稳定。</p>
 *
 * <h2>为何直接依赖 4 个 base 策略</h2>
 * <p>本类不再依赖 {@link TenancyStrategyFactory}，从源头消除了
 * {@code tenancyStrategyFactory → hybridStrategy → tenancyStrategyFactory}
 * 的 Spring Bean 循环依赖。{@link TenancyStrategyFactory} 仍保留
 * 用于 {@code TenantStrategyInterceptor} 通过 {@link IsolationMode} 反向查找
 * 本策略（单向依赖，不再成环）。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class HybridStrategy extends AbstractTenancyStrategy {

    private final ColumnStrategy columnStrategy;
    private final TableStrategy tableStrategy;
    private final SchemaStrategy schemaStrategy;
    private final DatabaseStrategy databaseStrategy;

    public HybridStrategy(MultiTenancyProperties properties,
                          TenantInfoProvider tenantInfoProvider,
                          ColumnStrategy columnStrategy,
                          TableStrategy tableStrategy,
                          SchemaStrategy schemaStrategy,
                          DatabaseStrategy databaseStrategy) {
        super(properties, tenantInfoProvider);
        this.columnStrategy = columnStrategy;
        this.tableStrategy = tableStrategy;
        this.schemaStrategy = schemaStrategy;
        this.databaseStrategy = databaseStrategy;
    }

    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.HYBRID;
    }

    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        validateTenantId(TenantContext.getTenantId());
        resolveDelegate(tenantInfo.getMode()).beforeSqlExecute(invocation, tenantInfo);
    }

    /**
     * 根据 {@link TenantInfo#getMode()} 选择派发目标策略。
     *
     * <p>{@link IsolationMode#HYBRID} 不应作为目标模式出现 — 租户的实际模式
     * 应始终是 4 种 base 模式之一。若出现则抛出业务异常，由调用方决定
     * 是修正 {@code TenantInfo} 还是修正配置。</p>
     */
    private TenancyStrategy resolveDelegate(IsolationMode mode) {
        return switch (mode) {
            case COLUMN -> columnStrategy;
            case TABLE -> tableStrategy;
            case SCHEMA -> schemaStrategy;
            case DATABASE -> databaseStrategy;
            case HYBRID -> throw new BusinessException(
                    "Hybrid strategy cannot delegate to itself for tenant: "
                            + TenantContext.getTenantId());
        };
    }
}
