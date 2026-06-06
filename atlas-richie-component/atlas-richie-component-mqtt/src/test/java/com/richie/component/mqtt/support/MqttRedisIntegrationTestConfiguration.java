package com.richie.component.mqtt.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.local.config.LocalCacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.mqtt.filter.datasource.impl.RedisDatasourceHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        LocalCacheAutoConfiguration.class
})
public class MqttRedisIntegrationTestConfiguration {

    @Bean
    RedisDatasourceHandlerImpl redisDatasourceHandler() {
        return new RedisDatasourceHandlerImpl();
    }
}
