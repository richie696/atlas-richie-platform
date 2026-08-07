/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.oauth.dcr.config;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration;
import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.dcr.DynamicClientRegistrationEndpoint;
import cn.richie696.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import cn.richie696.component.oauth.dcr.spi.ClientRegistrationStore;
import cn.richie696.component.oauth.dcr.support.DefaultClientIdMetadataDocumentResolver;
import cn.richie696.component.oauth.dcr.support.RedisClientRegistrationStore;
import cn.richie696.component.oauth.dcr.support.SSRFProtection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;

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
            ObjectProvider<OAuthCache> cacheProvider,
            @Value("${platform.component.oauth.dcr.allowed-domains:}") List<String> allowedDomains,
            @Value("${platform.component.oauth.dcr.ssrf-cache-ttl:3600}") long cacheTtlSeconds
    ) {
        return new SSRFProtection(cacheProvider.getIfAvailable(InMemoryOAuthCache::new), allowedDomains, cacheTtlSeconds);
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
            OAuthCache cache, SSRFProtection ssrfProtection) {
        return new DefaultClientIdMetadataDocumentResolver(cache, ssrfProtection);
    }

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationStore.class)
    public ClientRegistrationStore clientRegistrationStore(OAuthCache cache) {
        return new RedisClientRegistrationStore(cache);
    }

    @Bean
    @ConditionalOnMissingBean(DynamicClientRegistrationEndpoint.class)
    public DynamicClientRegistrationEndpoint dynamicClientRegistrationEndpoint(
            ClientRegistry clientRegistry,
            ClientIdMetadataDocumentResolver metadataResolver,
            SSRFProtection ssrfProtection,
            OAuth2Properties properties,
            ObjectProvider<ClientRegistrationStore> registrationStore
    ) {
        return new DynamicClientRegistrationEndpoint(clientRegistry, metadataResolver, ssrfProtection,
                properties, registrationStore.getIfAvailable());
    }
}
