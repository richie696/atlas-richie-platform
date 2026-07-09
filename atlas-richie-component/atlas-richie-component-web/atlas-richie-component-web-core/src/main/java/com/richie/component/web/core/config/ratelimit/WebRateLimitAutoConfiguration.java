/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config.ratelimit;

import com.richie.component.concurrency.registry.CircuitBreakerRegistry;
import com.richie.component.concurrency.registry.DefaultCircuitBreakerRegistry;
import com.richie.component.concurrency.registry.DefaultRateLimiterRegistry;
import com.richie.component.concurrency.registry.RateLimiterRegistry;
import com.richie.component.web.core.interceptor.CircuitBreakerInterceptor;
import com.richie.component.web.core.interceptor.RateLimitInterceptor;
import com.richie.component.web.core.metrics.WebMetrics;
import com.richie.component.web.core.spi.KeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Objects;

/**
 * 限流 / 熔断拦截器自动装配（README.md §5.2 A-3）。
 * <p>
 * 装配顺序：
 * <ol>
 *   <li>{@link RateLimiterRegistry} / {@link CircuitBreakerRegistry} 默认实现（如用户没自定义）</li>
 *   <li>{@link RateLimitInterceptor} / {@link CircuitBreakerInterceptor} 拦截器 bean</li>
 * </ol>
 *
 * <h2>启用条件</h2>
 * <ul>
 *   <li>依赖可选：concurrency 在 classpath 时此装配生效；缺失时 Spring 跳过（README.md §6 R7 选 B）</li>
 *   <li>{@code platform.component.web.rate-limit.enabled=true}（默认）/ circuit-breaker.enabled=true</li>
 * </ul>
 *
 * <h2>拦截器链</h2>
 * <p>两个拦截器通过 {@link org.springframework.core.Ordered} 接口注入顺序；由
 * {@link com.richie.component.web.core.servlet.InterceptingFilter} 通过
 * {@link org.springframework.beans.factory.ObjectProvider} 收集，按顺序驱动。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@AutoConfiguration
@org.springframework.boot.autoconfigure.AutoConfigureAfter(com.richie.component.web.core.config.ratelimit.WebKeyResolverAutoConfiguration.class)
@ConditionalOnClass({RateLimiterRegistry.class, CircuitBreakerRegistry.class})
@EnableConfigurationProperties({RateLimitProperties.class, CircuitBreakerProperties.class})
public class WebRateLimitAutoConfiguration {

    // ───────── Registry 默认实现 ─────────

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry() {
        return new DefaultRateLimiterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return new DefaultCircuitBreakerRegistry();
    }

    // ───────── RateLimit 拦截器 ─────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.rate-limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RateLimitInterceptor rateLimitInterceptor(RateLimiterRegistry registry,
                                                     RateLimitProperties properties,
                                                     ObjectProvider<KeyResolver> keyResolverProvider,
                                                     WebMetrics webMetrics) {
        KeyResolver keyResolver = keyResolverProvider.getIfAvailable();
        if (keyResolver == null) {
            log.warn("WebRateLimitAutoConfiguration: no KeyResolver bean available — RateLimitInterceptor will deny all (returning 401 client_unidentified). Define @Bean KeyResolver or set platform.component.web.rate-limit.enabled=false.");
            return new RateLimitInterceptor(registry, properties, ctx -> null, webMetrics);
        }
        return new RateLimitInterceptor(registry, properties, keyResolver, webMetrics);
    }

    // ───────── CircuitBreaker 拦截器 ─────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.circuit-breaker", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public CircuitBreakerInterceptor circuitBreakerInterceptor(CircuitBreakerRegistry registry,
                                                              CircuitBreakerProperties properties,
                                                              ObjectProvider<KeyResolver> keyResolverProvider,
                                                              WebMetrics webMetrics) {
        KeyResolver keyResolver = keyResolverProvider.getIfAvailable();
        if (keyResolver == null) {
            log.warn("WebRateLimitAutoConfiguration: no KeyResolver bean available — CircuitBreakerInterceptor will deny all. Define @Bean KeyResolver or set platform.component.web.circuit-breaker.enabled=false.");
            return new CircuitBreakerInterceptor(registry, properties, ctx -> null, webMetrics);
        }
        return new CircuitBreakerInterceptor(registry, properties, keyResolver, webMetrics);
    }
}