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
package com.richie.component.oauth.authz.config;

import com.richie.component.oauth.authz.AuthorizationCodeGrant;
import com.richie.component.oauth.authz.AuthorizationEndpoint;
import com.richie.component.oauth.authz.PKCESupport;
import com.richie.component.oauth.authz.support.DefaultAuthorizationCodeStore;
import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2AutoConfiguration;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.spi.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

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
    public AuthorizationCodeStore authorizationCodeStore() {
        return new DefaultAuthorizationCodeStore();
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
            OAuth2Properties properties
    ) {
        return new AuthorizationCodeGrant(tokenStore, clientRegistry, authorizationCodeStore, pkceSupport, properties);
    }
}
