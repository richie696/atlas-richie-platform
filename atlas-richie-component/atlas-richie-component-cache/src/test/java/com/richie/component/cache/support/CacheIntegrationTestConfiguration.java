package com.richie.component.cache.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.local.config.LocalCacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        LocalCacheAutoConfiguration.class
})
public class CacheIntegrationTestConfiguration {
}
