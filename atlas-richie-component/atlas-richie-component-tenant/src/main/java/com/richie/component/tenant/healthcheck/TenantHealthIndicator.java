package com.richie.component.tenant.healthcheck;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.spi.NoOpTenantInfoProvider;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动期 SPI 健康检查器。
 *
 * <p>Spring Boot 启动完成后（{@code ApplicationStartedEvent} 之后，
 * {@code ApplicationReadyEvent} 之前）执行:</p>
 * <ol>
 *   <li>检查当前 {@link TenantInfoProvider} Bean 是否仍为 {@link NoOpTenantInfoProvider} 占位实现</li>
 *   <li>若仍为占位实现，记录 ERROR 并拋 {@link IllegalStateException} 阻止应用启动完成</li>
 * </ol>
 *
 * <p><b>默认关闭</b>({@code multi-tenancy.health-check.enabled=false})。
 * 生产环境建议开启以避免上游 NoOp 静默导致所有 SQL 路由失效。</p>
 *
 * <p>典型失败信息:
 * <pre>
 * TenantInfoProvider is still NoOpTenantInfoProvider.
 * Please provide a real implementation (e.g. querying sys_tenant table)
 * and register it as a Spring Bean.
 * </pre>
 *
 * <h2>关闭方式</h2>
 * <ul>
 *   <li>开发环境 / 单元测试: 不启用此检查器(默认即关闭)</li>
 *   <li>生产 / 预发: 启用以强制接入方实现 SPI</li>
 * </ul>
 *
 * @author richie696
 * @since 2.1.0
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "multi-tenancy.health-check", name = "enabled", havingValue = "true")
public class TenantHealthIndicator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantHealthIndicator.class);

    private final TenantInfoProvider tenantInfoProvider;
    private final MultiTenancyProperties properties;

    public TenantHealthIndicator(TenantInfoProvider tenantInfoProvider, MultiTenancyProperties properties) {
        this.tenantInfoProvider = tenantInfoProvider;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Multi-tenancy disabled, TenantHealthIndicator skipped");
            return;
        }

        if (tenantInfoProvider instanceof NoOpTenantInfoProvider) {
            String message = "TenantInfoProvider is still NoOpTenantInfoProvider. "
                + "Multi-tenancy is enabled but no real SPI implementation has been provided. "
                + "Please implement TenantInfoProvider (e.g. querying sys_tenant table) "
                + "and register it as a Spring Bean to override the NoOp default.";
            log.error("[多租户] {}", message);
            throw new IllegalStateException(message);
        }

        log.info("[多租户] SPI 健康检查通过: TenantInfoProvider={}",
            tenantInfoProvider.getClass().getSimpleName());
    }
}
