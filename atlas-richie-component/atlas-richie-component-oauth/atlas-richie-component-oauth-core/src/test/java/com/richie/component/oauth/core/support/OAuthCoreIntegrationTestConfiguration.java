package com.richie.component.oauth.core.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.oauth.core.config.OAuth2AutoConfiguration;
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
        OAuth2AutoConfiguration.class,
})
public class OAuthCoreIntegrationTestConfiguration {

    @Bean
    public TokenStore tokenStore() {
        return new DefaultTokenStore();
    }
}
