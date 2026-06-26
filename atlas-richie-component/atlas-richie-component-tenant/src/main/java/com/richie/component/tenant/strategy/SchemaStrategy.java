package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema 隔离策略（SCHEMA 模式，仅 PostgreSQL / Oracle）。
 *
 * <p>通过 {@code SET LOCAL search_path TO schemaName} 切换 Schema，
 * 事务结束后自动恢复（LOCAL 修饰符保证仅对当前事务生效）。</p>
 *
 * <p><b>前置条件</b>：调用方必须在事务中（{@code @Transactional}）。
 * PostgreSQL 的 {@code SET LOCAL} 仅在事务内生效；若连接 {@code autoCommit=true}，
 * 该语句会被静默忽略，数据将写入错误的 schema 而不抛任何异常。
 * 本策略会预先检测并抛 {@link TenantErrorCode#TENANT_SCHEMA_REQUIRES_TRANSACTION} 阻断。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class SchemaStrategy extends AbstractTenancyStrategy {

    public SchemaStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.SCHEMA;
    }

    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        Long tenantId = TenantContext.getTenantId();
        validateTenantId(tenantId);

        String schemaName = tenantInfo.getSchemaName();
        if (schemaName == null || !schemaName.matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException("Schema name validation failed: " + schemaName);
        }

        try {
            Connection conn = getConnection(invocation);
            ensureTransactional(conn);
            if (properties.isSchemaAutoCreate() && !schemaExists(conn, schemaName)) {
                createSchemaWithTables(conn, schemaName);
            }
            Statement stmt = conn.createStatement();
            stmt.execute("SET LOCAL search_path TO %s".formatted(schemaName));
            stmt.close();
        } catch (SQLException e) {
            throw new BusinessException("Failed to switch schema to " + schemaName, e);
        }
    }

    /**
     * 校验连接处于事务中。PostgreSQL {@code SET LOCAL} 仅在事务内生效，
     * autoCommit=true 时静默失效，因此必须在执行前 fail-fast。
     */
    private void ensureTransactional(Connection conn) throws SQLException {
        if (conn.getAutoCommit()) {
            throw new BusinessException(
                TenantErrorCode.TENANT_SCHEMA_REQUIRES_TRANSACTION.name(),
                "Connection autoCommit=true; SET LOCAL search_path would be silently ignored. "
                    + "Wrap the call in @Transactional or use TransactionTemplate.");
        }
    }

    private Connection getConnection(Invocation invocation) {
        // BaseStatementHandler 在 MyBatis 3.5.x 已移除 getConnection()，改读 Invocation 入参
        Object arg = invocation.getArgs()[0];
        if (!(arg instanceof Connection conn)) {
            throw new BusinessException("Expected Connection as first argument of Invocation, got: "
                    + (arg == null ? "null" : arg.getClass().getName()));
        }
        return conn;
    }

    private boolean schemaExists(Connection conn, String schemaName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (schemaName.equals(rs.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createSchemaWithTables(Connection conn, String schemaName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS %s".formatted(schemaName));
        }
    }
}
