package com.richie.component.statemachine.storage.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.local.config.LocalCacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import com.richie.component.statemachine.storage.impl.RedisStateStorage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableConfigurationProperties(StateMachineProperties.class)
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        LocalCacheAutoConfiguration.class,
        RedisStateStorage.class,
        StateMachineKeyBuilder.class
})
public class StatemachineIntegrationTestConfiguration {
}
