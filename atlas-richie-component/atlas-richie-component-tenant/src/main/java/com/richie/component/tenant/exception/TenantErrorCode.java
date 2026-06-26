package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 多租户模块统一错误码枚举。
 * <p>
 * 所有多租户相关异常必须使用此枚举定义错误码和默认消息，
 * 禁止在业务代码中硬编码魔法字符串。
 *
 * @author richie696
 * @since 2026-06-10
 */
@Getter
public enum TenantErrorCode {

    // ==================== 身份认证（4.2 TenantIdentityFilter） ====================

    /**
     * 无认证 Token — 未登录或 Token 已过期
     */
    TENANT_AUTH_MISSING_TOKEN(401, "Missing authentication token"),

    /**
     * JWT 中 tenantId claim 为空字符串（非 null，null 表示超管跳过）
     */
    TENANT_AUTH_BLANK_CLAIM(403, "JWT tenantId claim is blank"),

    /**
     * tenantId 不是合法 Long 正整数 — 拒绝非法值
     */
    TENANT_AUTH_INVALID_FORMAT(403, "Invalid tenant ID: {0}"),

    /**
     * 租户账户已过期 — tenantExpiredTime &lt; 当前时间
     */
    TENANT_AUTH_EXPIRED(403, "Tenant account expired: {0}"),

    /**
     * JWT tenantId 与 X-Tenant-ID header 不一致 — 防止租户伪造
     */
    TENANT_AUTH_MISMATCH(403, "Tenant mismatch: JWT={0} vs Header={1}"),

    /**
     * 未知租户 — sys_tenant 表中不存在，引导通过管理接口注册
     */
    TENANT_IDENTITY_NOT_FOUND(403, "Unknown tenant: {0}. Please register via management API first."),

    // ==================== 数据源与熔断（6.x / TenantStrategyInterceptor） ====================

    /**
     * 策略调度时找不到租户信息 — TenantInfoProvider 返回 null
     */
    TENANT_NOT_FOUND(404, "Tenant not found: {0}"),

    /**
     * 独立 tenant 数据源熔断中 — 仅影响该租户
     */
    TENANT_DATA_SOURCE_UNAVAILABLE(503, "Tenant data source is currently unavailable: {0}"),

    /**
     * shared 数据源熔断中 — 影响 column/table/schema 模式全租户
     */
    TENANT_SHARED_DS_UNAVAILABLE(503, "Shared data source is currently unavailable"),

    // ==================== 事务管理（4.x TransactionalAspect） ====================

    /**
     * 事务内禁止切换租户 — 同一事务必须操作同一租户数据源
     */
    TENANT_SWITCH_IN_TRANSACTION(403, "Cannot switch tenant from {0} to {1} within a transaction"),

    // ==================== 生命周期（sys_tenant status） ====================

    /**
     * 租户迁移中 — 暂时拒绝访问
     */
    TENANT_MIGRATING(503, "Tenant {0} is currently migrating, please try again later"),

    /**
     * 隔离模式迁移被拒绝 — 管理接口限制
     */
    TENANT_MODE_MIGRATION_DENIED(403, "Mode migration denied for tenant {0}: {1}"),

    /**
     * 租户开通失败 — 资源分配错误
     */
    TENANT_PROVISION_FAILED(500, "Tenant provision failed: {0}"),

    /**
     * 非管理员访问管理接口 — 需要 platform administrator 角色
     */
    TENANT_ADMIN_REQUIRED(403, "Admin API access requires platform administrator role"),

    // ==================== Schema 隔离前置条件（SchemaStrategy） ====================

    /**
     * Schema 隔离必须在事务中执行 — PostgreSQL SET LOCAL search_path 仅在事务内生效，
     * autoCommit=true 时静默失效，写入数据会落到错误 schema。
     * 解决方案：在调用方加 @Transactional 或 TransactionTemplate。
     */
    TENANT_SCHEMA_REQUIRES_TRANSACTION(500, "Schema isolation requires an active transaction (autoCommit must be false)"),

    // ==================== Table 模式前置条件（TableStrategy） ====================

    /**
     * Table 模式的表后缀非法 — 后缀将直接拼接到 SQL 表名（如 {@code products_1001}），
     * 必须通过白名单 {@code ^[a-zA-Z0-9_]+$} 校验以防止 SQL 注入。
     * 常见错误：sys_tenant.tableSuffix 字段包含空格、分号、引号、Unicode 字符。
     * 解决方案：修正 sys_tenant 中该租户的 tableSuffix，仅使用字母/数字/下划线。
     */
    TENANT_TABLE_SUFFIX_INVALID(500, "Table suffix validation failed: {0}. Must match ^[a-zA-Z0-9_]+$");

    // ==================== 枚举定义 ====================

    /**
     * HTTP 状态码
     */
    private final int httpStatus;

    /**
     * 默认错误消息（支持 MessageFormat 占位符 {0}, {1}, ...）
     */
    private final String defaultMessage;

    TenantErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 错误码（枚举名，如 TENANT_AUTH_MISSING_TOKEN）。
     * 用作 {@code ApiResult.code} / {@code ResultVO.code}。
     *
     * @return 错误码字符串
     */
    public String getCode() {
        return name();
    }

    /**
     * 使用 MessageFormat 填充占位符后的消息。
     *
     * @param args 占位符参数
     * @return 格式化后的消息
     */
    public String format(Object... args) {
        return java.text.MessageFormat.format(defaultMessage, args);
    }
}
