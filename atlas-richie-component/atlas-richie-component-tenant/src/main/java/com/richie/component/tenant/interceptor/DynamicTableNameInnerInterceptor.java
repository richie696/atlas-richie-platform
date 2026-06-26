package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TableSuffixHolder;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MyBatis 动态表名改写拦截器（Table / Hybrid-TABLE 模式）。
 *
 * <p>在 {@link StatementHandler#prepare} 执行前，从 {@link TableSuffixHolder}
 * 读取当前租户的表后缀，对 SQL 中的表名追加后缀。</p>
 *
 * <p>仅在 {@link TableSuffixHolder} 已设置非空后缀时生效；忽略表通过
 * {@code MultiTenancyProperties.ignoreTables} 配置排除。改写失败时降级原 SQL 执行。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DynamicTableNameInnerInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DynamicTableNameInnerInterceptor.class);

    private final MultiTenancyProperties properties;

    public DynamicTableNameInnerInterceptor(MultiTenancyProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }

        String suffix = TableSuffixHolder.get();
        if (suffix == null || suffix.isBlank()) {
            return invocation.proceed();
        }

        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();

        try {
            String modifiedSql = rewriteTableNames(originalSql, suffix);
            if (!originalSql.equals(modifiedSql)) {
                setBoundSqlField(boundSql, modifiedSql);
                if (log.isDebugEnabled()) {
                    log.debug("Table name rewritten: {} -> {}", originalSql, modifiedSql);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to rewrite table names for tenant suffix '{}', proceeding with original SQL: {}",
                suffix, e.getMessage());
        }

        return invocation.proceed();
    }

    /**
     * 解析并改写 SQL，对 FROM / JOIN / UPDATE / DELETE / INSERT 的表名追加后缀。
     */
    private String rewriteTableNames(String sql, String suffix) throws Exception {
        Statement statement = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
        Set<String> ignoreTables = properties.getIgnoreTables().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        if (statement instanceof Select select) {
            rewriteSelect(select, suffix, ignoreTables);
        } else if (statement instanceof Update update) {
            applySuffix(update.getTable(), suffix, ignoreTables);
        } else if (statement instanceof Delete delete) {
            applySuffix(delete.getTable(), suffix, ignoreTables);
        } else if (statement instanceof Insert insert) {
            applySuffix(insert.getTable(), suffix, ignoreTables);
        }

        return statement.toString();
    }

    private void rewriteSelect(Select select, String suffix, Set<String> ignoreTables) {
        if (!(select instanceof PlainSelect plainSelect)) {
            return;
        }
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table table) {
            applySuffix(table, suffix, ignoreTables);
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getRightItem() instanceof Table joinTable) {
                    applySuffix(joinTable, suffix, ignoreTables);
                }
            }
        }
    }

    /**
     * 对单表追加后缀；当表名匹配 ignoreTables 时跳过。
     */
    private void applySuffix(Table table, String suffix, Set<String> ignoreTables) {
        if (table == null || table.getName() == null) {
            return;
        }
        String name = table.getName();
        if (ignoreTables.contains(name.toLowerCase())) {
            return;
        }
        // 幂等保护：若已含后缀则不重复追加（防止多次拦截叠加）
        if (name.endsWith(suffix)) {
            return;
        }
        table.setName(name + suffix);
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
