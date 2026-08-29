/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.web.core.config.ratelimit;

import cn.richie696.component.concurrency.registry.CircuitBreakerRegistry;
import cn.richie696.component.concurrency.registry.DefaultCircuitBreakerRegistry;
import cn.richie696.component.web.core.interceptor.CircuitBreakerInterceptor;
import cn.richie696.component.web.core.metrics.WebMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Servlet 业务熔断自动装配；分布式业务限流位于可选 web-rate-limiter 模块。 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreakerRegistry.class)
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class WebCircuitBreakerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return new DefaultCircuitBreakerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.circuit-breaker", name = "enabled", havingValue = "true")
    public CircuitBreakerInterceptor circuitBreakerInterceptor(CircuitBreakerRegistry registry,
                                                               CircuitBreakerProperties properties,
                                                               WebMetrics webMetrics) {
        return new CircuitBreakerInterceptor(registry, properties, webMetrics);
    }
}
