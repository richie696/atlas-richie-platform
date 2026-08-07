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
 * OAuth 2.1 授权码模块自动装配
 * <p>
 * 通过条件装配启用授权码模块，依赖 oauth-core 配置。
 * 仅在 {@code platform.component.oauth.enabled=true} 时生效。
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
