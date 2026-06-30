package com.richie.component.tenant.healthcheck;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动期 Schema 校验器。
 *
 * <p>Spring Boot 启动完成后（{@code ApplicationReadyEvent} 之前）执行:</p>
 * <ol>
 *   <li>检查 {@code multi-tenancy.ignore-tables} 配置的表是否真实存在(防 typo)</li>
 *   <li>检查 {@code multi-tenancy.startup-validation.schema-tables} 配置的表是否含
 *       {@code tenantIdColumn} 列(防业务表缺列导致 SQL 改写失败)</li>
 * </ol>
 *
 * <p><b>仅在 COLUMN 模式下生效</b>。其他模式(SCHEMA/TABLE/DATABASE)无此问题,跳过校验。</p>
 *
 * <p><b>默认关闭</b>({@code multi-tenancy.startup-validation.enabled=false})。
 * 业务方需在 application.yml 中显式配置 {@code schema-tables} 列表后启用,
 * 否则启动期无法知道要校验哪些业务表。</p>
 *
 * <p>典型失败信息:
 * <pre>
 * Schema validation failed: Table 'ordrs' listed in multi-tenancy.ignore-tables does not exist
 * Schema validation failed: Table 'orders' is missing tenant_id column 'tenant_id' required by multi-tenancy
 * </pre>
 *
 * <h2>为什么需要显式配置 schemaTables</h2>
 * <p>通过 {@code information_schema} 反射所有表太慢且有副作用(锁竞争、跨 schema 误报)。
 * 业务方显式声明要校验的表,既保证启动速度,也保证校验准确性。</p>
 *
 * @author richie696
 * @since 2.1.0
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "multi-tenancy.startup-validation", name = "enabled", havingValue = "true")
@ConditionalOnBean(DataSource.class)
public class StartupSchemaValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSchemaValidator.class);

    private final DataSource dataSource;
    private final MultiTenancyProperties properties;

    public StartupSchemaValidator(DataSource dataSource, MultiTenancyProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Multi-tenancy disabled, StartupSchemaValidator skipped");
            return;
        }
        if (properties.getMode() != IsolationMode.COLUMN) {
            log.debug("StartupSchemaValidator only applies to COLUMN mode, current={}", properties.getMode());
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            validateIgnoreTables(meta);
            validateSchemaTables(meta);
        } catch (SQLException e) {
            throw new BusinessException(
                "Failed to query database metadata for startup schema validation", e);
        }

        log.info("[多租户] 启动期 Schema 校验通过: ignoreTables={}, schemaTables={}",
            properties.getIgnoreTables(), properties.getStartupValidation().getSchemaTables());
    }

    private void validateIgnoreTables(DatabaseMetaData meta) throws SQLException {
        List<String> ignoreTables = properties.getIgnoreTables();
        if (ignoreTables == null || ignoreTables.isEmpty()) {
            return;
        }
        Set<String> existing = collectTableNames(meta);
        for (String tableName : ignoreTables) {
            if (!existing.contains(tableName.toLowerCase())) {
                String message = "Table '" + tableName + "' listed in multi-tenancy.ignore-tables does not exist";
                log.error("[多租户] {}", message);
                throw new BusinessException(
                    TenantErrorCode.TENANT_IGNORE_TABLE_NOT_FOUND.name(), message);
            }
        }
    }

    private void validateSchemaTables(DatabaseMetaData meta) throws SQLException {
        List<String> schemaTables = properties.getStartupValidation().getSchemaTables();
        if (schemaTables == null || schemaTables.isEmpty()) {
            log.debug("multi-tenancy.startup-validation.schema-tables is empty, skip column check");
            return;
        }
        String tenantColumn = properties.getTenantIdColumn();
        for (String tableName : schemaTables) {
            Set<String> columns = collectColumnNames(meta, tableName);
            if (!columns.contains(tenantColumn.toLowerCase())) {
                String message = "Table '" + tableName + "' is missing tenant_id column '"
                    + tenantColumn + "' required by multi-tenancy";
                log.error("[多租户] {}", message);
                throw new BusinessException(
                    TenantErrorCode.TENANT_TENANT_ID_COLUMN_MISSING.name(), message);
            }
        }
    }

    private Set<String> collectTableNames(DatabaseMetaData meta) throws SQLException {
        Set<String> tables = new LinkedHashSet<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private Set<String> collectColumnNames(DatabaseMetaData meta, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }
}