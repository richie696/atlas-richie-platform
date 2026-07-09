/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config.mvc;

import com.richie.component.web.core.hook.DefaultHookBus;
import com.richie.component.web.core.hook.HookBus;
import com.richie.component.web.core.servlet.InterceptingFilter;
import com.richie.component.web.core.spi.WebInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * 把 web-core 的 {@link WebInterceptor} SPI 接入 Spring MVC servlet 过滤器链。
 * <p>
 * <strong>背景</strong>：web-core 的限流 / 熔断 / HangDetection / 平台防护等拦截器实现
 * {@link WebInterceptor} SPI（容器无关），但 Spring MVC 默认<strong>不</strong>驱动这套 SPI——
 * 配置项可解析、{@code @Bean} 可创建，但 HTTP 请求<strong>不经过</strong>这些拦截器。
 * 本配置类把所有 {@code WebInterceptor} bean 聚合成一个 {@link InterceptingFilter}，
 * 注册为 servlet {@code Filter}，排在 {@code DispatcherServlet} 之前——
 * 拦截器可在 controller 执行前短路（拒绝请求时不调用下游 chain.doFilter）。
 *
 * <h2>配置驱动（铁律）</h2>
 * <p>每个拦截器 bean 是否存在由其自身 {@code @ConditionalOnProperty(enabled=true)} 决定——
 * 本类不做"额外启用"决策，仅负责把"已创建的"拦截器串起来。{@code enabled=false} →
 * 对应 bean 不存在 → 拦截器列表里就没它 → 完全不生效。
 *
 * <h2>拦截器顺序</h2>
 * <p>每个 {@link WebInterceptor} 实现 {@link org.springframework.core.Ordered#getOrder()}，
 * 自身有明确顺序值（{@code PlatformProtection=100} / {@code AnomalyDetection=200} /
 * {@code RateLimit=300} / {@code CircuitBreaker=400} / {@code HangDetection=500}）。
 * 本类按 {@code getOrder()} 升序排序后再注入 chain。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(InterceptingFilter.class)
@org.springframework.boot.autoconfigure.AutoConfigureAfter({
        com.richie.component.web.core.config.ratelimit.WebKeyResolverAutoConfiguration.class,
        com.richie.component.web.core.config.ratelimit.WebRateLimitAutoConfiguration.class,
        com.richie.component.web.core.config.hang.HangAutoConfiguration.class,
        com.richie.component.web.core.config.protection.PlatformProtectionAutoConfiguration.class
})
public class WebMvcInterceptorsAutoConfiguration {

    /**
     * 注册 {@link InterceptingFilter} 为 servlet 过滤器，{@code order=10} 保证排在
     * Spring Security / Spring MVC 其它过滤器之前（但晚于 {@code CharacterEncodingFilter} 等基础过滤器）。
     * <p>URL pattern 为 {@code /*} —— 所有 HTTP 请求都进入 web-core 拦截器链。
     */
    @org.springframework.context.annotation.Bean
    public FilterRegistrationBean<InterceptingFilter> interceptingFilterRegistration(
            org.springframework.beans.factory.ObjectFactory<List<WebInterceptor>> interceptorsFactory,
            ObjectProvider<HookBus> hookBusProvider) {
        // 用 ObjectFactory 延迟拉取：FilterRegistrationBean 的 getFilter() 在 servlet 容器启动
        // 时才被调用——此时所有 auto-config @Bean（包括 RateLimit / CircuitBreaker 拦截器，
        // 由各自 @ConditionalOnBean(KeyResolver.class) 触发的）都应已就绪。
        FilterRegistrationBean<InterceptingFilter> reg = new FilterRegistrationBean<>() {
            @Override
            public InterceptingFilter getFilter() {
                List<WebInterceptor> ordered = interceptorsFactory.getObject().stream()
                        .sorted((a, b) -> Integer.compare(orderOf(a), orderOf(b)))
                        .toList();
                HookBus hookBus = hookBusProvider.getIfAvailable(DefaultHookBus::new);
                InterceptingFilter filter = new InterceptingFilter(ordered, hookBus);
                log.info("WebMvcInterceptorsAutoConfiguration: instantiating InterceptingFilter with {} interceptor(s) [{}]",
                        ordered.size(),
                        ordered.stream().map(i -> i.getClass().getSimpleName()).toList());
                return filter;
            }
        };
        reg.setOrder(10);
        reg.addUrlPatterns("/*");
        reg.setName("webCoreInterceptingFilter");
        return reg;
    }

    private static int orderOf(WebInterceptor i) {
        return (i instanceof org.springframework.core.Ordered o) ? o.getOrder() : Integer.MAX_VALUE;
    }
}