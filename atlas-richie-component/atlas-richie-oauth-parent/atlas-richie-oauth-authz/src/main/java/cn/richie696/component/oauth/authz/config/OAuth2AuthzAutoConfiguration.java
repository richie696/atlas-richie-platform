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
package cn.richie696.component.oauth.authz.config;

import cn.richie696.component.oauth.authz.AuthorizationCodeGrant;
import cn.richie696.component.oauth.authz.AuthorizationEndpoint;
import cn.richie696.component.oauth.authz.PKCESupport;
import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import cn.richie696.component.oauth.authz.support.DefaultAuthorizationCodeStore;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration;
import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.spi.TokenStore;
import cn.richie696.component.oauth.core.spi.AccessTokenSigner;
import cn.richie696.component.oauth.core.spi.AccessTokenClaimsCustomizer;
import cn.richie696.component.oauth.core.ClientAuthenticationService;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;

/**
 * oauth-authz 模块的 Spring Boot 自动装配入口。
 * <p>
 * 通过 {@link Bean} + {@link ConditionalOnMissingBean} 显式注册 {@link AuthorizationCodeStore}/
 * {@link PKCESupport}/{@link AuthorizationEndpoint}/{@link AuthorizationCodeGrant};在
 * {@code platform.component.oauth.enabled=true} 时随 {@link Import} 引入的 oauth-core
 * {@link OAuth2AutoConfiguration} 一同生效。
 * </p>
 * <p>
 * 处于 oauth 组件的模块装配层位置:不持有业务逻辑,只把 oauth-authz 包内的协议服务接入 Spring 容器,
 * 下游是 OAuth Service 在 HTTP 适配层注入这些 Bean。
 * </p>
 * <p>
 * 解决的问题:让用户在配置 platform.component.oauth.enabled=true 后自动获得授权码流程所需的全部
 * Bean;同时通过 {@code ConditionalOnMissingBean} 把每个 Bean 都暴露为可替换的 SPI,业务方可以
 * 注入自己的 AuthorizationCodeStore 或 PKCESupport 而无需禁用整个模块。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.component.oauth", name = "enabled", havingValue = "true")
@Import(OAuth2AutoConfiguration.class)
public class OAuth2AuthzAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthorizationCodeStore.class)
    public AuthorizationCodeStore authorizationCodeStore(ObjectProvider<OAuthCache> cacheProvider) {
        return new DefaultAuthorizationCodeStore(cacheProvider.getIfAvailable(InMemoryOAuthCache::new));
    }

    @Bean
    @ConditionalOnMissingBean(PKCESupport.class)
    public PKCESupport pkceSupport() {
        return new PKCESupport();
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationEndpoint.class)
    public AuthorizationEndpoint authorizationEndpoint(
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authorizationCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties
    ) {
        return new AuthorizationEndpoint(clientRegistry, authorizationCodeStore, pkceSupport, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationCodeGrant.class)
    public AuthorizationCodeGrant authorizationCodeGrant(
            TokenStore tokenStore,
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authorizationCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties,
            AccessTokenSigner accessTokenSigner,
            AccessTokenClaimsCustomizer claimsCustomizer,
            ClientAuthenticationService clientAuthenticationService
    ) {
        return new AuthorizationCodeGrant(tokenStore, clientRegistry, authorizationCodeStore, pkceSupport,
                properties, accessTokenSigner, claimsCustomizer, clientAuthenticationService);
    }
}
