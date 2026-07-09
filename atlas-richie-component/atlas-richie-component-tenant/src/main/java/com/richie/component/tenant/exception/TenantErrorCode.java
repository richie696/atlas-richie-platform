/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    TENANT_TABLE_SUFFIX_INVALID(500, "Table suffix validation failed: {0}. Must match ^[a-zA-Z0-9_]+$"),

    // ==================== 命名规范校验（NamingConventionValidator） ====================

    /**
     * SQL 标识符白名单校验失败 — 适用于 schema 名、table 后缀、dataSource key 等
     * 任何会被拼接到 SQL 字符串或用于 DataSource 路由的命名。
     * 白名单规则：{@code ^[A-Za-z_][A-Za-z0-9_]*$}，长度 1-128 字符。
     * 常见错误：含空格、分号、引号、连字符、Unicode、长度超限。
     * 解决方案：修正命名，仅使用字母/数字/下划线，且以字母或下划线开头。
     */
    TENANT_INVALID_NAMING(500, "Naming convention violation: {0}"),

    // ==================== 启动期 Schema 校验（StartupSchemaValidator） ====================

    /**
     * 业务表缺少租户 ID 列 — {@code multi-tenancy.tenant-id-column} 配置的列
     * 在 {@code multi-tenancy.startup-validation.schema-tables} 列出的某张表中不存在。
     * 常见错误：表 schema 漂移、tenant_id 列名拼错、表建表脚本未包含租户列。
     * 解决方案：在该表中添加 {@code ALTER TABLE xxx ADD COLUMN tenant_id BIGINT},
     * 或调整 {@code schema-tables} 配置去除该表。
     */
    TENANT_TENANT_ID_COLUMN_MISSING(500,
        "Table {0} is missing tenant_id column '{1}' required by multi-tenancy"),

    /**
     * 配置在 {@code multi-tenancy.ignore-tables} 的表名在数据库中不存在。
     * 常见错误：表名拼写错误、schema 漂移、拼大小写不一致（注意 PG 默认折叠小写）。
     * 解决方案：修正 {@code ignore-tables} 配置中的表名，或确认该表应在多租户 SQL 改写范围内。
     */
    TENANT_IGNORE_TABLE_NOT_FOUND(500,
        "Table '{0}' listed in multi-tenancy.ignore-tables does not exist in the database");

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
