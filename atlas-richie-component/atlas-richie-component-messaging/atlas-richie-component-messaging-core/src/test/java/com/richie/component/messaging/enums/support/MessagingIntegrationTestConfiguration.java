package com.richie.component.messaging.enums.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.messaging.filter.datasource.impl.MemoryDatasourceHandlerImpl;
import com.richie.component.messaging.filter.datasource.impl.RedisDatasourceHandlerImpl;
import com.richie.component.messaging.filter.handler.impl.MessageHandlerServiceImpl;
import com.richie.component.messaging.pulsar.config.MessagingProperties;
import com.richie.context.common.api.SpringContextHolder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.cloud.configuration.CompatibilityVerifierAutoConfiguration"
})
@EnableConfigurationProperties(MessagingProperties.class)
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        SpringContextHolder.class,
        MemoryDatasourceHandlerImpl.class,
        RedisDatasourceHandlerImpl.class,
        MessageHandlerServiceImpl.class,
})
public class MessagingIntegrationTestConfiguration {
}
