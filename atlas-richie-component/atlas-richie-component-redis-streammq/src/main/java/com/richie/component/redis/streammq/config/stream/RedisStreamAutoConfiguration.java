package com.richie.component.redis.streammq.config.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Stream 消费者自动配置
 *
 * <p>启用消费者配置属性，支持通过 YAML 配置多个消费者
 *
 * @author richie696
 * @since 2025-12-09
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({
        RedisStreamProperties.class,
        RedisStreamProperties.CleanupConfig.class
})
@ConditionalOnProperty(prefix = "platform.cache.redis.stream.consumers", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamAutoConfiguration {

    /**
     * 构造函数
     */
    public RedisStreamAutoConfiguration() {
        log.info("Redis Stream 消费者自动配置已启用");
    }
}
