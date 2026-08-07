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
 * oauth-dcr 模块的 Spring Boot 自动装配入口。
 * <p>
 * 通过 {@link Bean} + {@link ConditionalOnMissingBean} 显式注册 {@link SSRFProtection}/
 * {@link ClientIdMetadataDocumentResolver}/{@link ClientRegistrationStore}/{@link DynamicClientRegistrationEndpoint};
 * 在 {@code platform.component.oauth.enabled=true} 时随 {@link Import} 引入的 oauth-core
 * {@link OAuth2AutoConfiguration} 一同生效。SSRF 防护的参数(白名单域名、DNS 缓存 TTL)走
 * {@code platform.component.oauth.dcr.*} 配置。
 * </p>
 * <p>
 * 处于 oauth 组件的模块装配层位置:不持有业务逻辑,只把 oauth-dcr 包内的协议服务与安全策略接入 Spring 容器;
 * 下游是 OAuth Service 在 HTTP 适配层暴露 RFC 7591 的 /register 端点。
 * </p>
 * <p>
 * 解决的问题:让 DCR 模块在 oauth-core 启用后自动生效,并把 SSRF 防护、客户端元数据解析器、注册存储
 * 三个 SPI 全部暴露为可替换的 Bean,业务方可以注入数据库事务版 ClientRegistrationStore 或自定义
 * 域名白名单,无需禁用整个模块。
 * </p>
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
