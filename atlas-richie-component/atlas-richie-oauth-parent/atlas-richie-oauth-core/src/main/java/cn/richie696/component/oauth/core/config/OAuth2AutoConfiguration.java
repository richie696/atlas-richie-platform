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
package cn.richie696.component.oauth.core.config;

import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.ScopeResolver;
import cn.richie696.component.oauth.core.TokenEndpoint;
import cn.richie696.component.oauth.core.spi.TokenStore;
import cn.richie696.component.oauth.core.support.DefaultTokenStore;
import cn.richie696.component.oauth.core.support.CacheBackedTokenStore;
import cn.richie696.component.oauth.core.ClientAuthenticationService;
import cn.richie696.component.oauth.core.DeviceAuthorizationService;
import cn.richie696.component.oauth.core.spi.DeviceAuthorizationStore;
import cn.richie696.component.oauth.core.support.CacheBackedDeviceAuthorizationStore;
import cn.richie696.component.oauth.core.spi.ScopePolicyRepository;
import cn.richie696.component.oauth.core.support.GlobalCacheScopePolicyRepository;
import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.core.support.HmacAccessTokenSigner;
import cn.richie696.component.oauth.core.spi.AccessTokenSigner;
import cn.richie696.component.oauth.core.spi.AccessTokenClaimsCustomizer;
import cn.richie696.component.oauth.core.spi.OAuthAuditSink;
import cn.richie696.component.oauth.core.support.LoggingOAuthAuditSink;
import cn.richie696.component.oauth.core.spi.ClientRepository;
import cn.richie696.component.oauth.cache.OAuthCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * OAuth 2.1 协议内核的 Spring Boot 自动装配入口。
 * <p>
 * 配置前缀 {@code platform.component.oauth};所有 OAuth Bean 均通过 {@link Bean} 方法显式注册,
 * 不使用 {@code @ComponentScan},确保只有在 {@code platform.component.oauth.enabled=true} 时才会加载。
 * </p>
 * <p>
 * 处于整个 oauth 组件的总装配层位置:被 oauth-authz/oauth-dcr 的 AutoConfiguration 通过
 * {@code @Import} 引入,使两个子模块无需重复声明 oauth-core 依赖;{@link ObjectProvider} 让每个
 * Bean 都可以被业务方提供的自定义实现覆盖。
 * </p>
 * <p>
 * 解决的问题:让 oauth 组件"按需启用" —— 用户不配置 enabled=true 时整个 oauth 上下文不加载;
 * 同时通过 {@link ConditionalOnMissingBean} 把 9 个核心 SPI/服务都暴露为可替换的 Bean,业务方可以
 * 局部替换某个能力而不必禁用整个组件。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@AutoConfiguration
@EnableConfigurationProperties(OAuth2Properties.class)
@ConditionalOnProperty(prefix = "platform.component.oauth", name = "enabled", havingValue = "true")
public class OAuth2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore tokenStore(ObjectProvider<OAuthCache> cacheProvider) {
        OAuthCache cache = cacheProvider.getIfAvailable(InMemoryOAuthCache::new);
        return new CacheBackedTokenStore(cache);
    }

    @Bean
    @ConditionalOnMissingBean(ClientRegistry.class)
    public ClientRegistry clientRegistry(ObjectProvider<ClientRepository> repositoryProvider,
                                         ObjectProvider<OAuthCache> cacheProvider) {
        ClientRepository repository = repositoryProvider.getIfAvailable();
        return repository == null
                ? new ClientRegistry(cacheProvider.getIfAvailable(InMemoryOAuthCache::new))
                : new ClientRegistry(repository);
    }

    @Bean
    @ConditionalOnMissingBean(ClientAuthenticationService.class)
    public ClientAuthenticationService clientAuthenticationService(ClientRegistry clientRegistry) {
        return new ClientAuthenticationService(clientRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(DeviceAuthorizationStore.class)
    public DeviceAuthorizationStore deviceAuthorizationStore(ObjectProvider<OAuthCache> cacheProvider) {
        return new CacheBackedDeviceAuthorizationStore(cacheProvider.getIfAvailable(InMemoryOAuthCache::new));
    }

    @Bean
    @ConditionalOnMissingBean(DeviceAuthorizationService.class)
    public DeviceAuthorizationService deviceAuthorizationService(
            ClientRegistry clientRegistry, DeviceAuthorizationStore store, OAuth2Properties properties) {
        return new DeviceAuthorizationService(clientRegistry, store,
                properties.getDeviceVerificationUri(), properties.getDeviceExpiresInSeconds(),
                properties.getDevicePollingIntervalSeconds());
    }

    @Bean
    @ConditionalOnMissingBean(ScopePolicyRepository.class)
    public ScopePolicyRepository scopePolicyRepository() {
        return new GlobalCacheScopePolicyRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ScopeResolver.class)
    public ScopeResolver scopeResolver(ScopePolicyRepository scopePolicyRepository) {
        return new ScopeResolver(scopePolicyRepository);
    }

    @Bean
    @ConditionalOnMissingBean(AccessTokenSigner.class)
    public AccessTokenSigner accessTokenSigner(OAuth2Properties properties) {
        return new HmacAccessTokenSigner(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AccessTokenClaimsCustomizer.class)
    public AccessTokenClaimsCustomizer accessTokenClaimsCustomizer() {
        return AccessTokenClaimsCustomizer.empty();
    }

    @Bean
    @ConditionalOnMissingBean(OAuthAuditSink.class)
    public OAuthAuditSink oauthAuditSink() {
        return new LoggingOAuthAuditSink();
    }

    @Bean
    @ConditionalOnMissingBean(TokenEndpoint.class)
    public TokenEndpoint tokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                                       OAuth2Properties properties, AccessTokenSigner accessTokenSigner,
                                       ObjectProvider<OAuthCache> cacheProvider,
                                       AccessTokenClaimsCustomizer claimsCustomizer,
                                       OAuthAuditSink auditSink,
                                       ClientAuthenticationService clientAuthenticationService,
                                       DeviceAuthorizationService deviceAuthorizationService) {
        return new TokenEndpoint(tokenStore, clientRegistry, properties, accessTokenSigner,
                cacheProvider.getIfAvailable(), claimsCustomizer, auditSink,
                clientAuthenticationService, deviceAuthorizationService);
    }
}
