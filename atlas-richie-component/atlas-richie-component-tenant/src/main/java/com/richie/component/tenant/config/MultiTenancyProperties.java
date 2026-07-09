/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.config;

import com.richie.component.tenant.model.IsolationMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多租户统一配置模型。
 *
 * <p>对应 YAML 前缀 {@code multi-tenancy}。所有可配置项均可通过 Nacos / 环境变量覆盖。
 * 标有 {@code @RefreshScope} 的字段支持热更新（如 canary tenants、circuit breaker 阈值）。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Data
@ConfigurationProperties(prefix = "multi-tenancy")
public class MultiTenancyProperties {

    /**
     * 多租户功能总开关。{@code false} 时所有租户拦截器 / 策略 / 填充器跳过执行。
     */
    private boolean enabled = true;

    /**
     * 默认隔离模式。Hybrid 模式下各租户可在 {@code sys_tenant} 中指定自己的模式。
     */
    private IsolationMode mode = IsolationMode.COLUMN;

    /**
     * 请求头中租户 ID 的 key（出站/入站统一）。
     * 默认 {@code X-Tenant-ID}。
     */
    private String tenantIdHeader = "X-Tenant-ID";

    /**
     * 是否强制要求认证态与租户归属一致（JWT tenantId 必须存在且合法）。
     */
    private boolean enforceAuthTenant = true;

    /**
     * 数据库中租户 ID 列名。Column / Hybrid 模式 SQL 注入条件使用此名称。
     */
    private String tenantIdColumn = "tenant_id";

    /**
     * 忽略租户隔离的表名列表（如字典表、配置表）。
     * 这些表不会被 SQL 注入 {@code tenant_id} 条件，也不会被表名替换。
     */
    private List<String> ignoreTables = new ArrayList<>();

    /**
     * Table 模式下的表名后缀模板。{@code ${tenant}} 会被替换为租户 ID。
     * 例：{@code "_${tenant}"} → {@code "_1001"}
     */
    private String tableNameSuffix = "_${tenant}";

    /**
     * Schema 模式下的 Schema 名前缀。
     * 例：{@code "tenant_"} + tenantId → {@code "tenant_1001"}
     */
    private String schemaPrefix = "tenant_";

    /**
     * Schema 模式下，当 Schema 不存在时是否自动创建（含表结构复制）。
     */
    private boolean schemaAutoCreate = false;

    /**
     * 强制使用 {@code TransmittableThreadLocal} 降级模式，跳过 {@code ScopedValue}。
     * 仅在 JVM 不支持 ScopedValue 或需要与传统 ThreadLocal 生态集成时设置。
     */
    private boolean forceThreadLocal = false;

    /**
     * 是否为微服务架构（默认 {@code true}）。
     *
     * <p>开启时启动检测微服务通信框架（HTTP / gRPC），确保租户上下文可跨服务透传。
     * 若未检测到任何通信框架，输出 WARN 日志。
     *
     * <p>单体应用设置为 {@code false} 即可跳过检测，避免无意义的告警。
     */
    private boolean microservice = true;

    /**
     * 数据源配置（shared + 各租户独立数据源）。
     */
    private DataSourceConfig datasource = new DataSourceConfig();

    /**
     * 灰度配置。
     */
    private CanaryConfig canary = new CanaryConfig();

    /**
     * 熔断器配置。
     */
    private CircuitBreakerConfig circuit = new CircuitBreakerConfig();

    /**
     * 健康探测配置。
     */
    private HealthProbeConfig health = new HealthProbeConfig();

    /**
     * 租户信息缓存配置（{@link com.richie.component.tenant.spi.CachingTenantInfoProvider}）。
     *
     * <p>默认开启，大幅降低 {@code TenantInfoProvider.getTenantInfo()} 调用频率。
     * 若需关闭，设置 {@code multi-tenancy.cache.tenant-info.enabled=false}。</p>
     */
    private TenantInfoCacheConfig cache = new TenantInfoCacheConfig();

    /**
     * 启动期 SPI 健康检查配置（{@link com.richie.component.tenant.healthcheck.TenantHealthIndicator}）。
     *
     * <p>默认关闭。生产环境建议开启以确保 {@link com.richie.component.tenant.spi.TenantInfoProvider}
     * 被业务方实际实现（而非 NoOp 占位）。</p>
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /**
     * 启动期 Schema 校验配置。默认关闭；启用后会在应用启动完成前校验
     * {@code ignore-tables} 中的表是否存在以及 {@code schema-tables} 中的表是否含
     * {@link #tenantIdColumn} 列。
     */
    private StartupValidationConfig startupValidation = new StartupValidationConfig();

    // ==================== 嵌套配置类 ====================

    /**
     * 数据源配置：共享数据源 + 各租户独立数据源。
     */
    @Data
    public static class DataSourceConfig {

        /**
         * 共享数据源配置（Column / Table / Schema 模式必须）。
         */
        private SharedDataSourceConfig shared = new SharedDataSourceConfig();

        /**
         * 租户独立数据源配置（Database / Hybrid 模式使用）。
         * key 为 String 类型的租户数据源标识（如 {@code "1001"}）。
         */
        private Map<String, TenantDataSourceConfig> tenants = new HashMap<>();
    }

    /**
     * 共享数据源（所有 Column/Table/Schema 模式租户共用）。
     */
    @Data
    public static class SharedDataSourceConfig {
        private String url;
        private String username;
        private String password;
        private HikariConfig hikari = new HikariConfig();
    }

    /**
     * 租户独立数据源（Database 模式每个租户一个）。
     */
    @Data
    public static class TenantDataSourceConfig {
        private String url;
        private String username;
        private String password;
        /**
         * 灰度数据源 URL（canary 模式下使用，与全量数据源物理隔离）。
         */
        private String canaryUrl;
        /**
         * 若为 null 则继承 shared 的 Hikari 配置。
         */
        private HikariConfig hikari;
    }

    /**
     * HikariCP 连接池配置子集。
     */
    @Data
    public static class HikariConfig {
        private int maximumPoolSize = 0;
        private int minimumIdle = 0;
        private long idleTimeout = 0;
        private long connectionTimeout = 0;
    }

    /**
     * 灰度配置：控制哪些租户走灰度通道、灰度比例。
     */
    @Data
    public static class CanaryConfig {
        private List<CanaryTenant> tenants = new ArrayList<>();
    }

    /**
     * 灰度租户定义。
     */
    @Data
    public static class CanaryTenant {
        /**
         * 租户 ID（Long 类型）。
         */
        private Long id;
        /**
         * 灰度比例（0-100），100 表示全量灰度。
         */
        private int ratio = 100;
    }

    /**
     * 熔断器配置。
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * 连续失败次数达到此阈值后触发熔断。
         */
        private int failureThreshold = 5;
        /**
         * 熔断打开后等待时间（毫秒），超时进入半开状态。
         */
        private long openWindowMs = 30_000;
    }

    /**
     * 健康探测配置。
     */
    @Data
    public static class HealthProbeConfig {
        /**
         * 探测间隔（毫秒）。
         */
        private long probeIntervalMs = 30_000;
    }

    /**
     * 租户信息缓存配置。
     */
    @Data
    public static class TenantInfoCacheConfig {
        /**
         * 是否启用 {@link com.richie.component.tenant.spi.CachingTenantInfoProvider} 装饰器。
         * 默认 {@code true}（框架自动为 {@link TenantInfoProvider} 叠加本地 TTL 缓存）。
         */
        private boolean enabled = true;

        /**
         * 缓存项 TTL（秒）。推荐 30-300，过短则命中率低，过长则 sys_tenant 变更生效延迟。
         */
        private long ttlSeconds = 60;

        /**
         * 缓存最大租户数。超过则按 LRU 时间淘汰一半，{@code <= 0} 表示不限制。
         */
        private int maxSize = 10_000;
    }

    /**
     * 启动期 SPI 健康检查配置。
     */
    @Data
    public static class HealthCheckConfig {
        /**
         * 是否启用 {@link com.richie.component.tenant.healthcheck.TenantHealthIndicator}。
         * 启用后若 {@link com.richie.component.tenant.spi.TenantInfoProvider} 仍是 NoOp 占位，
         * 应用启动失败。默认 {@code false}。
         */
        private boolean enabled = false;
    }

    /**
     * 启动期 Schema 校验配置。
     */
    @Data
    public static class StartupValidationConfig {
        /**
         * 是否启用 {@link com.richie.component.tenant.healthcheck.StartupSchemaValidator}。
         * 默认 {@code false}（业务方按需开启）。
         */
        private boolean enabled = false;

        /**
         * 需要校验 {@code tenantIdColumn} 列是否存在的业务表清单。
         * 仅在 {@code mode=COLUMN} 模式下生效。
         */
        private List<String> schemaTables = new ArrayList<>();
    }
}
