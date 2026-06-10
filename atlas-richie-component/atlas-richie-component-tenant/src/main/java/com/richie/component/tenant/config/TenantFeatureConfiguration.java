package com.richie.component.tenant.config;

import com.richie.contract.model.TenantFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 租户功能标志自动配置
 *
 * <p>在任何环境下（Servlet / WebFlux）将 {@link TenantFeature} 的全局标志置为 {@code true}，
 * 标识 {@code atlas-richie-component-tenant} 已加载到 classpath 中。</p>
 *
 * <p>{@link TenantAutoConfiguration} 带有 {@code @ConditionalOnWebApplication(SERVLET)} 限制，
 * 该配置类没有环境限制，确保在 WebFlux（例如网关）中也能注册标志位。</p>
 *
 * @author richie696
 * @since 1.0
 */
@AutoConfiguration
public class TenantFeatureConfiguration {

    @PostConstruct
    public void init() {
        TenantFeature.setEnabled(true);
    }
}
