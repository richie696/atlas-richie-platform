package com.richie.component.oauth.authz.config;

import com.richie.component.oauth.authz.support.DefaultAuthorizationCodeStore;
import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.core.config.OAuth2AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * OAuth 2.1 授权码模块自动装配
 * <p>
 * 通过条件装配启用授权码模块，依赖 oauth-core 配置。
 *
 * @author richie696
 * @since 2026-06-12
 */
@AutoConfiguration
@ComponentScan("com.richie.component.oauth.authz")
@Import(OAuth2AutoConfiguration.class)
public class OAuth2AuthzAutoConfiguration {

    @Bean
    public AuthorizationCodeStore authorizationCodeStore() {
        return new DefaultAuthorizationCodeStore();
    }
}
