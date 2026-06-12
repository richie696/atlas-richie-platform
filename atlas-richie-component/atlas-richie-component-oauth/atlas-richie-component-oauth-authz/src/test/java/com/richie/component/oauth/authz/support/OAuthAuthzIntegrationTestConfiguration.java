package com.richie.component.oauth.authz.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.oauth.authz.config.OAuth2AuthzAutoConfiguration;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.component.oauth.core.support.DefaultTokenStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        OAuth2AuthzAutoConfiguration.class,
})
public class OAuthAuthzIntegrationTestConfiguration {

    @Bean
    public TokenStore tokenStore() {
        return new DefaultTokenStore();
    }
}
