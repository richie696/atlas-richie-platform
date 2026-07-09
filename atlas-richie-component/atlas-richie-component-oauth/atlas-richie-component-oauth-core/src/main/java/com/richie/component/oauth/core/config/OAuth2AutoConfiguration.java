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
package com.richie.component.oauth.core.config;

import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.ScopeResolver;
import com.richie.component.oauth.core.TokenEndpoint;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.component.oauth.core.support.DefaultTokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OAuth 2.1 自动装配
 * <p>
 * 通过条件装配启用组件，配置前缀 {@code platform.component.oauth}。
 * <p>
 * 所有 OAuth Bean 均通过 {@link Bean} 方法显式注册，不使用 {@code @ComponentScan}，
 * 确保只有在 {@code platform.component.oauth.enabled=true} 时才会加载。
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
    public TokenStore tokenStore() {
        return new DefaultTokenStore();
    }

    @Bean
    @ConditionalOnMissingBean(ClientRegistry.class)
    public ClientRegistry clientRegistry() {
        return new ClientRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(ScopeResolver.class)
    public ScopeResolver scopeResolver() {
        return new ScopeResolver();
    }

    @Bean
    @ConditionalOnMissingBean(TokenEndpoint.class)
    public TokenEndpoint tokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry, OAuth2Properties properties) {
        return new TokenEndpoint(tokenStore, clientRegistry, properties);
    }
}
