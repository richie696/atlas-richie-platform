package com.richie.component.tenant.model;

/**
 * 多租户隔离模式枚举。
 *
 * <p>定义五种标准隔离模式，每种模式对应不同的数据隔离策略。
 * 在 Hybrid 模式下，每个租户固定使用其中一种模式。</p>
 *
 * @author richie696
 * @since 2.0
 */
public enum IsolationMode {

    /**
     * 列隔离：共享数据库、共享表，通过 {@code tenant_id} 列区分租户数据。
     * SQL 自动注入 {@code WHERE tenant_id = ?} 条件。
     */
    COLUMN,

    /**
     * 表隔离：共享数据库，每个租户独立表（表名后缀区分）。
     * SQL 执行前动态替换表名。
     */
    TABLE,

    /**
     * Schema 隔离：共享数据库实例，每个租户独立 Schema。
     * SQL 执行前通过 {@code SET LOCAL search_path} 切换 Schema（仅 PostgreSQL/Oracle）。
     */
    SCHEMA,

    /**
     * 数据库隔离：每个租户独立数据库实例。
     * 通过 {@code AbstractRoutingDataSource} 路由到租户专属数据源。
     */
    DATABASE,

    /**
     * 混合模式：按租户固定隔离模式。
     * 运行时根据 {@code TenantInfo.mode} 分派到对应的策略实现。
     */
    HYBRID
}
