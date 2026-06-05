package com.richie.component.redis.streammq.config.stream;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis Stream 消费幂等去重配置（自 cache 模块迁移）。
 */
@Data
@ConfigurationProperties(prefix = "spring.data.redis.stream-idempotency")
public class RedisStreamIdempotencyProperties {

    private String keyPrefix = "idemp:stream:";

    private Duration memoryTtl = Duration.ofHours(1);

    private Duration redisTtl = Duration.ofHours(24);
}
