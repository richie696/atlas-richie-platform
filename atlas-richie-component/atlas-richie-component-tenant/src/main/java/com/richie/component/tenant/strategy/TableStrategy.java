package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TableSuffixHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.apache.ibatis.plugin.Invocation;

import java.util.regex.Pattern;

/**
 * 表隔离策略（TABLE 模式）。
 *
 * <p>将租户的表后缀写入 {@link TableSuffixHolder}，
 * 由 {@code DynamicTableNameInnerInterceptor} 读取后替换表名（如 {@code products} → {@code products_1001}）。</p>
 *
 * <p><b>前置条件</b>：租户的 {@code tableSuffix} 必须通过白名单校验
 * （{@code ^[a-zA-Z0-9_]+$}），因为它会被直接拼接到 SQL 表名中。
 * 包含特殊字符（空格、分号、引号、Unicode 等）的后缀会导致 SQL 注入或语法错误。
 * 本策略会预先检测并抛 {@link TenantErrorCode#TENANT_TABLE_SUFFIX_INVALID} 阻断。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TableStrategy extends AbstractTenancyStrategy {

    /**
     * 表后缀白名单：仅允许字母、数字、下划线。
     * 与 SchemaStrategy 的 schemaName 校验保持一致的口径。
     */
    private static final Pattern TABLE_SUFFIX_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public TableStrategy(MultiTenancyProperties properties, TenantInfoProvider tenantInfoProvider) {
        super(properties, tenantInfoProvider);
    }

    @Override
    public boolean supports(IsolationMode mode) {
        return mode == IsolationMode.TABLE;
    }

    @Override
    public void beforeSqlExecute(Invocation invocation, TenantInfo tenantInfo) {
        assertTenantPresent();
        Long tenantId = TenantContext.getTenantId();
        validateTenantId(tenantId);

        String tableSuffix = tenantInfo.getTableSuffix();
        validateTableSuffix(tableSuffix);
        TableSuffixHolder.set(tableSuffix);
    }

    /**
     * 校验表后缀通过白名单 {@code ^[a-zA-Z0-9_]+$}。
     * 后缀会直接拼接到 SQL 表名，必须 fail-fast 阻断特殊字符以防 SQL 注入。
     */
    private void validateTableSuffix(String tableSuffix) {
        if (tableSuffix == null || tableSuffix.isEmpty() || !TABLE_SUFFIX_PATTERN.matcher(tableSuffix).matches()) {
            throw new BusinessException(
                TenantErrorCode.TENANT_TABLE_SUFFIX_INVALID.name(),
                "Table suffix validation failed: " + tableSuffix + " (must match ^[a-zA-Z0-9_]+$)");
        }
    }
}
