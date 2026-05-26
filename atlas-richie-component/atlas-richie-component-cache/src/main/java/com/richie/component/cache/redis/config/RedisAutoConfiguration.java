package com.richie.component.cache.redis.config;

import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.cache.redis.config.monitor.RedisStreamMonitoringAutoConfiguration;
import com.richie.component.cache.redis.config.stream.RedisStreamAutoConfiguration;
import com.richie.component.cache.redis.config.tracing.RedisStreamTracingAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis 缓存服务自动配置类
 *
 * @author richie696
 * @since 2.0.0
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.cache")
@Import({
        RedisBaseAutoConfiguration.class,
        RedisStreamAutoConfiguration.class,
        RedisStreamMonitoringAutoConfiguration.class,
        RedisStreamTracingAutoConfiguration.class
})
public class RedisAutoConfiguration {

    /**
     * 构造函数
     */
    public RedisAutoConfiguration() {
        log.info("初始化Redis缓存服务配置模块");
    }

}
