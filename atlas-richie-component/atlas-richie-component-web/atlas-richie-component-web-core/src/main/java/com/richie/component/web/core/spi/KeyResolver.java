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
package com.richie.component.web.core.spi;

import com.richie.component.web.core.config.ratelimit.WebFilterProperties;

/**
 * ClientKey 解析器（README.md §4.1 RateLimit 配置 {@code key-resolver}）。
 * <p>
 * {@link com.richie.component.web.core.interceptor.RateLimitInterceptor} 与
 * {@link com.richie.component.web.core.interceptor.CircuitBreakerInterceptor} 都依赖此 SPI
 * 决定按哪个 key 限流 / 熔断。
 *
 * <h2>典型实现</h2>
 * <ul>
 *   <li>{@code HeaderBasedKeyResolver}（默认）：从 {@code X-Client-Id} / {@code X-Tenant-Id} 取</li>
 *   <li>{@code TokenBasedKeyResolver}（§4.9 阶段）：从 JWT 的 {@code sub} 字段取</li>
 *   <li>{@code CompositeKeyResolver}：多个 KeyResolver 按优先级串接</li>
 * </ul>
 *
 * <h2>返回 null 的语义</h2>
 * <p>返回 {@code null} 表示"无法识别客户端"——RateLimit / CB 应视为配置错误，
 * 由拦截器统一 mark short-circuit 401（不可放行到下游）。
 *
 * @author richie696
 * @since 2026-07
 */
@FunctionalInterface
public interface KeyResolver {

    /**
     * 解析当前请求的限流 / 熔断键。
     *
     * @param ctx 当前请求上下文
     * @return 非空 clientKey；返回 {@code null} 时拦截器统一按"未识别"短路
     */
    String resolve(WebRequestContext ctx);

    /**
     * 默认实现：{@code HeaderBasedKeyResolver}，从 {@code X-Client-Id} header 取。
     * <p>由 {@link com.richie.component.web.core.config.WebKeyResolverAutoConfiguration}
     * 在用户未自定义时自动装配。
     */
    static KeyResolver defaultResolver(WebFilterProperties props) {
        return new com.richie.component.web.core.spi.support.HeaderBasedKeyResolver(
                props.getKeyHeader());
    }
}