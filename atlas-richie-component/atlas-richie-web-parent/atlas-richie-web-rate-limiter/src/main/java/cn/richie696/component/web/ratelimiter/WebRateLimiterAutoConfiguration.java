/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.web.ratelimiter;

import cn.richie696.component.cache.ops.LimiterOps;
import cn.richie696.component.web.core.config.ratelimit.RateLimitProperties;
import cn.richie696.component.web.core.config.ratelimit.WebKeyResolverAutoConfiguration;
import cn.richie696.component.web.core.metrics.WebMetrics;
import cn.richie696.component.web.core.spi.KeyResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 引入该模块且开启配置后，才把 Redis 业务限流加入 web 拦截器链。 */
@AutoConfiguration
@AutoConfigureAfter(WebKeyResolverAutoConfiguration.class)
@ConditionalOnClass(LimiterOps.class)
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebRateLimiterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.rate-limit", name = "enabled", havingValue = "true")
    public DistributedRateLimitInterceptor distributedRateLimitInterceptor(LimiterOps limiter,
                                                                            RateLimitProperties properties,
                                                                            ObjectProvider<KeyResolver> keyResolver,
                                                                            WebMetrics metrics) {
        KeyResolver resolver = keyResolver.getIfAvailable();
        if (resolver == null) {
            resolver = ignored -> null;
        }
        return new DistributedRateLimitInterceptor(limiter, properties, resolver, metrics);
    }
}
