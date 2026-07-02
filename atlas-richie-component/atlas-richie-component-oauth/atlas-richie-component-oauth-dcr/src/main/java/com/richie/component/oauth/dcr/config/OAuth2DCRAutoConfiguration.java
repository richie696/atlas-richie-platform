package com.richie.component.oauth.dcr.config;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2AutoConfiguration;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.dcr.DynamicClientRegistrationEndpoint;
import com.richie.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.DefaultClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.SSRFProtection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * OAuth 2.0 DCR 自动装配
 * <p>
 * 通过条件装配启用动态客户端注册组件，配置前缀 {@code platform.component.oauth-dcr}
 * 仅在 {@code platform.component.oauth.enabled=true} 时生效。
 *
 * @author richie696
 * @since 2026-06-12
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.component.oauth", name = "enabled", havingValue = "true")
@Import(OAuth2AutoConfiguration.class)
public class OAuth2DCRAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SSRFProtection.class)
    public SSRFProtection ssrfProtection(
            GlobalCache globalCache,
            @Value("${platform.component.oauth.dcr.allowed-domains:}") List<String> allowedDomains,
            @Value("${platform.component.oauth.dcr.ssrf-cache-ttl:3600}") long cacheTtlSeconds
    ) {
        return new SSRFProtection(globalCache, allowedDomains, cacheTtlSeconds);
    }

    /**
     * 注册默认的 ClientIdMetadataDocument 解析器。
     * <p>
     * 使用 {@link ConditionalOnMissingBean} 保证业务方可以
     * 通过声明自定义 {@link ClientIdMetadataDocumentResolver} Bean 来替换默认实现（SPI 扩展点）。
     */
    @Bean
    @ConditionalOnMissingBean(ClientIdMetadataDocumentResolver.class)
    public ClientIdMetadataDocumentResolver clientIdMetadataDocumentResolver(
            GlobalCache globalCache, SSRFProtection ssrfProtection) {
        return new DefaultClientIdMetadataDocumentResolver(globalCache, ssrfProtection);
    }

    @Bean
    @ConditionalOnMissingBean(DynamicClientRegistrationEndpoint.class)
    public DynamicClientRegistrationEndpoint dynamicClientRegistrationEndpoint(
            ClientRegistry clientRegistry,
            ClientIdMetadataDocumentResolver metadataResolver,
            SSRFProtection ssrfProtection,
            OAuth2Properties properties
    ) {
        return new DynamicClientRegistrationEndpoint(clientRegistry, metadataResolver, ssrfProtection, properties);
    }
}
