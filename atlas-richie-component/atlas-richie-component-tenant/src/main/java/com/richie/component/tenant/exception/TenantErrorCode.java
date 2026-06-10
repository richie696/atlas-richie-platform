package com.richie.component.tenant.exception;

/**
 * 多租户模块统一错误码枚举。
 * <p>
 * 所有多租户相关异常必须使用此枚举定义错误码和默认消息，
 * 禁止在业务代码中硬编码魔法字符串。
 *
 * @author richie696
 * @since 2026-06-10
 */
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
     * tenantId 不符合 allowed-tenant-pattern 白名单 — 拒绝非法字符注入
     */
    TENANT_AUTH_INVALID_FORMAT(403, "Invalid tenant code format: {0}"),

    /**
     * 租户账户已过期 — tenantExpiredTime < 当前时间
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
    TENANT_NOT_FOUND(500, "Tenant not found: {0}"),

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
    TENANT_SWITCH_IN_TRANSACTION(500, "Cannot switch tenant from {0} to {1} within a transaction"),

    // ==================== 生命周期（sys_tenant status） ====================

    /**
     * 非管理员访问管理接口 — 需要 platform administrator 角色
     */
    TENANT_ADMIN_REQUIRED(403, "Admin API access requires platform administrator role");

    // ==================== 枚举定义 ====================

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 默认错误消息（支持 MessageFormat 占位符 {0}, {1}, ...） */
    private final String defaultMessage;

    TenantErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
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
     * 默认错误消息（支持 MessageFormat 占位符 {0}, {1}, ...）。
     *
     * @return 默认消息模板
     */
    public String getDefaultMessage() {
        return defaultMessage;
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
