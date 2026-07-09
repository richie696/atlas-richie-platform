/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.config.degrade.DegradeAutoConfiguration;
import com.richie.component.web.core.config.degrade.DegradeProperties;
import com.richie.component.web.core.config.hang.HangAutoConfiguration;
import com.richie.component.web.core.config.hang.HangDetectionProperties;
import com.richie.component.web.core.config.login.LoginConfig;
import com.richie.component.web.core.config.metrics.MetricsAutoConfiguration;
import com.richie.component.web.core.config.mvc.CorsProperties;
import com.richie.component.web.core.config.protection.PlatformProtectionProperties;
import com.richie.component.web.core.config.ratelimit.CircuitBreakerProperties;
import com.richie.component.web.core.config.ratelimit.RateLimitProperties;
import com.richie.component.web.core.config.ratelimit.WebFilterProperties;
import com.richie.component.web.core.config.ratelimit.WebRateLimitAutoConfiguration;
import com.richie.component.web.core.config.reload.HotReloadAutoConfiguration;
import com.richie.component.web.core.config.sse.SseAutoConfiguration;
import com.richie.component.web.core.config.tracing.TracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Web 组件库主配置（README.md §5.2）。
 * <p>
 * Spring Boot 通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 仅加载本类；其它域配置（Metrics / Tracing / Reload / Hang / Degrade / RateLimit / CircuitBreaker / SSE）
 * 通过 {@link Import} 在本类一次性聚合，避免业务方引入多个独立 AutoConfiguration 类的间接依赖。
 *
 * <h2>域配置清单</h2>
 * <ul>
 *   <li>{@link MetricsAutoConfiguration}：指标门面 {@code WebMetrics} 装配（§4.1 / §4.2 / §4.4 / §4.8）</li>
 *   <li>{@link TracingAutoConfiguration}：追踪拦截器（§4.3）</li>
 *   <li>{@link HotReloadAutoConfiguration}：热刷新注册中心 + Spring Cloud Config 桥接（§4.6）</li>
 *   <li>{@link HangAutoConfiguration}：Hang 检测拦截器 + Watchdog 调度（§4.4）</li>
 *   <li>{@link DegradeAutoConfiguration}：降级拦截器 + 策略注册中心（§4.7）</li>
 *   <li>{@link WebRateLimitAutoConfiguration}：限流 / 熔断拦截器 + Registry 默认实现（§4.1 / §4.2）</li>
 *   <li>{@link SseAutoConfiguration}：SSE 长连接管理器（§4.4）</li>
 * </ul>
 *
 * <h2>Properties 聚合</h2>
 * <p>本类通过 {@link EnableConfigurationProperties} 注册 web 主控 + 各子域 Properties 类
 * （与 {@link WebProperties} 的 {@code @NestedConfigurationProperty} 字段一一对应）：
 * <ul>
 *   <li>{@link WebProperties}：web 主控聚合根（prefix {@code platform.component.web}），
 *       内部 {@code @NestedConfigurationProperty} 持有所有子域引用</li>
 *   <li>{@link LoginConfig} / {@link CorsProperties}：登录 / CORS（§5.1）</li>
 *   <li>{@link RateLimitProperties} / {@link CircuitBreakerProperties}：限流 / 熔断（§4.1 / §4.2）</li>
 *   <li>{@link WebFilterProperties}：过滤器通用（KeyResolver header 等）</li>
 *   <li>{@link HangDetectionProperties}：Hang 检测（§4.4）</li>
 *   <li>{@link DegradeProperties}：降级（§4.7）</li>
 *   <li>{@link PlatformProtectionProperties}：平台防护层（§4.8）</li>
 *   <li>{@link BusinessIntegrationProperties}：业务能力集成（§4.9）</li>
 * </ul>
 *
 * <h2>启用条件</h2>
 * <p>本类<strong>无</strong> {@code @ConditionalOnXxx} 注解——作为聚合入口始终装配；各域装配自身带
 * {@code @ConditionalOnClass} / {@code @ConditionalOnBean}，缺依赖时自动跳过。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@Import({
        MetricsAutoConfiguration.class,
        TracingAutoConfiguration.class,
        HotReloadAutoConfiguration.class,
        HangAutoConfiguration.class,
        DegradeAutoConfiguration.class,
        WebRateLimitAutoConfiguration.class,
        SseAutoConfiguration.class
})
@EnableConfigurationProperties({
        WebProperties.class,
        LoginConfig.class,
        CorsProperties.class,
        RateLimitProperties.class,
        CircuitBreakerProperties.class,
        WebFilterProperties.class,
        HangDetectionProperties.class,
        DegradeProperties.class,
        PlatformProtectionProperties.class,
        BusinessIntegrationProperties.class
})
public class WebAutoConfiguration {
}