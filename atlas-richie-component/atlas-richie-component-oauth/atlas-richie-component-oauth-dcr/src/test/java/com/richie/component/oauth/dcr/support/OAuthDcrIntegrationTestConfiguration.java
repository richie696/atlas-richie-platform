package com.richie.component.oauth.dcr.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.component.oauth.core.support.DefaultTokenStore;
import com.richie.component.oauth.dcr.config.OAuth2DCRAutoConfiguration;
import com.richie.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.DefaultClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.SSRFProtection;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        OAuth2DCRAutoConfiguration.class,
})
public class OAuthDcrIntegrationTestConfiguration {

    @Bean
    public TokenStore tokenStore() {
        return new DefaultTokenStore();
    }

    @Bean
    public ClientIdMetadataDocumentResolver clientIdMetadataDocumentResolver(
            GlobalCache globalCache,
            SSRFProtection ssrfProtection) {
        return new DefaultClientIdMetadataDocumentResolver(globalCache, ssrfProtection);
    }
}
