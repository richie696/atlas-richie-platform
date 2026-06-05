package com.richie.component.redis.streammq.config.stream;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 缓存提供者守卫：cache-provider 非 REDIS 时阻止 streammq 组件加载。
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'!='REDIS'")
public class RedisStreamProviderGuard {

    private final Environment environment;

    public RedisStreamProviderGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void failFast() {
        String provider = environment.getProperty("platform.cache.cache-provider", "REDIS");
        throw new IllegalStateException(
                "redis-streammq 组件依赖于 REDIS 缓存提供者，但当前配置为: " + provider + "。\n" +
                        "请将 platform.cache.cache-provider 设置为 REDIS，或移除 redis-streammq 依赖。");
    }
}
