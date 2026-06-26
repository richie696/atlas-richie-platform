package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MyBatis SQL 改写拦截器（Column / Hybrid-COLUMN 模式）。
 *
 * <p>在 {@link StatementHandler#prepare} 执行前，对 SELECT / UPDATE / DELETE
 * 自动追加 {@code tenant_id = ?} 条件；对 INSERT 自动追加 {@code tenant_id} 列和值。</p>
 *
 * <p>仅在隔离模式为 {@link IsolationMode#COLUMN}（或 {@link IsolationMode#HYBRID}
 * 且当前租户实际为 COLUMN 模式）时生效。TABLE / SCHEMA / DATABASE 模式不需要
 * {@code tenant_id} 列改写，本拦截器自动跳过以避免
 * {@code column "tenant_id" does not exist} 错误。忽略表通过
 * {@code ignoreTables} 配置排除。</p>
 *
 * <p>HYBRID 模式下会通过 {@link TenantInfoProvider} 解析当前租户的实际模式，
 * 接入方应在实现中加缓存以避免每次 SQL 都触发远程查询。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class TenantLineInnerInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantLineInnerInterceptor.class);

    private final MultiTenancyProperties properties;
    private final TenantInfoProvider tenantInfoProvider;

    /**
     * 构造 SQL 改写拦截器。
     *
     * @param properties         多租户配置（{@code multi-tenancy.*}）
     * @param tenantInfoProvider 租户信息提供方,用于读取租户实际隔离模式以决定是否改写
     */
    /**
 * 构造 SQL 改写拦截器。
 *
 * @param properties         多租户配置（{@code multi-tenancy.*}）
 * @param tenantInfoProvider 租户信息提供方,用于根据租户实际 mode 决定是否触发改写
 */
    /**
 * 构造 SQL 改写拦截器。
 *
 * @param properties         多租户配置（{@code multi-tenancy.*}）
 * @param tenantInfoProvider 租户信息提供方（HYBRID 模式下查租户实际模式）
 */
public TenantLineInnerInterceptor(MultiTenancyProperties properties,
                                  TenantInfoProvider tenantInfoProvider) {
    this.properties = properties;
    this.tenantInfoProvider = tenantInfoProvider;
}

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return invocation.proceed();
        }

        if (!shouldRewriteForCurrentMode(tenantId)) {
            return invocation.proceed();
        }

        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();

        try {
            String modifiedSql = processSql(originalSql, tenantId);
            if (!originalSql.equals(modifiedSql)) {
                setBoundSqlField(boundSql, modifiedSql);
                if (log.isDebugEnabled()) {
                    log.debug("Tenant SQL rewritten: {} -> {}", originalSql, modifiedSql);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to rewrite SQL for tenant isolation, proceeding with original SQL: {}",
                e.getMessage());
        }

        return invocation.proceed();
    }

    /**
     * 判断当前租户是否需要 SQL 改写。
     *
     * <ul>
     *   <li>COLUMN 模式：始终改写</li>
     *   <li>HYBRID 模式：查询租户实际模式，仅当为 COLUMN 时改写</li>
     *   <li>TABLE / SCHEMA / DATABASE 模式：跳过改写（表结构不含 {@code tenant_id} 列）</li>
     * </ul>
     */
    private boolean shouldRewriteForCurrentMode(Long tenantId) {
        IsolationMode mode = properties.getMode();
        if (mode == IsolationMode.COLUMN) {
            return true;
        }
        if (mode == IsolationMode.HYBRID) {
            try {
                TenantInfo info = tenantInfoProvider.getTenantInfo(tenantId);
                return info != null && info.getMode() == IsolationMode.COLUMN;
            } catch (Exception e) {
                log.warn("Failed to resolve tenant mode for line interceptor (tenant={}), "
                        + "skipping line rewrite: {}", tenantId, e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 解析并改写 SQL，追加 tenant_id 条件。
     */
    private String processSql(String sql, Long tenantId) throws Exception {
        Statement statement = CCJSqlParserUtil.parse(sql);
        Set<String> ignoreTables = properties.getIgnoreTables().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        String tenantColumn = properties.getTenantIdColumn();

        if (statement instanceof Select select) {
            processSelect(select, tenantId, tenantColumn, ignoreTables);
        } else if (statement instanceof Update update) {
            processUpdate(update, tenantId, tenantColumn, ignoreTables);
        } else if (statement instanceof Delete delete) {
            processDelete(delete, tenantId, tenantColumn, ignoreTables);
        } else if (statement instanceof Insert insert) {
            processInsert(insert, tenantId, tenantColumn, ignoreTables);
        }

        return statement.toString();
    }

    private void processSelect(Select select, Long tenantId, String tenantColumn,
                               Set<String> ignoreTables) {
        if (select instanceof PlainSelect plainSelect) {
            String tableName = extractTableName(plainSelect.getFromItem());
            if (tableName != null && !ignoreTables.contains(tableName.toLowerCase())) {
                Expression tenantCondition = buildTenantCondition(tenantColumn, tenantId);
                if (plainSelect.getWhere() == null) {
                    plainSelect.setWhere(tenantCondition);
                } else {
                    plainSelect.setWhere(new AndExpression(plainSelect.getWhere(), tenantCondition));
                }
            }
        }
    }

    private void processUpdate(Update update, Long tenantId, String tenantColumn,
                               Set<String> ignoreTables) {
        String tableName = update.getTable() != null ? update.getTable().getName() : null;
        if (tableName != null && !ignoreTables.contains(tableName.toLowerCase())) {
            Expression tenantCondition = buildTenantCondition(tenantColumn, tenantId);
            if (update.getWhere() == null) {
                update.setWhere(tenantCondition);
            } else {
                update.setWhere(new AndExpression(update.getWhere(), tenantCondition));
            }
        }
    }

    private void processDelete(Delete delete, Long tenantId, String tenantColumn,
                               Set<String> ignoreTables) {
        String tableName = delete.getTable() != null ? delete.getTable().getName() : null;
        if (tableName != null && !ignoreTables.contains(tableName.toLowerCase())) {
            Expression tenantCondition = buildTenantCondition(tenantColumn, tenantId);
            if (delete.getWhere() == null) {
                delete.setWhere(tenantCondition);
            } else {
                delete.setWhere(new AndExpression(delete.getWhere(), tenantCondition));
            }
        }
    }

    private void processInsert(Insert insert, Long tenantId, String tenantColumn,
                               Set<String> ignoreTables) {
        String tableName = insert.getTable() != null ? insert.getTable().getName() : null;
        if (tableName != null && !ignoreTables.contains(tableName.toLowerCase())) {
            List<Column> columns = insert.getColumns();
            boolean alreadyHasTenant = columns.stream()
                .anyMatch(c -> tenantColumn.equalsIgnoreCase(c.getColumnName()));
            if (!alreadyHasTenant) {
                columns.add(new Column(tenantColumn));
                if (insert.getValues() != null) {
                    insert.getValues().addExpressions(new LongValue(tenantId));
                }
            }
        }
    }

    private Expression buildTenantCondition(String tenantColumn, Long tenantId) {
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(tenantColumn));
        equalsTo.setRightExpression(new LongValue(tenantId));
        return equalsTo;
    }

    private String extractTableName(net.sf.jsqlparser.statement.select.FromItem fromItem) {
        if (fromItem instanceof net.sf.jsqlparser.schema.Table table) {
            return table.getName();
        }
        return null;
    }

    /**
     * 通过反射设置 BoundSql 的 sql 字段。
     */
    private void setBoundSqlField(BoundSql boundSql, String sql) throws Exception {
        Field field = BoundSql.class.getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, sql);
    }
}
