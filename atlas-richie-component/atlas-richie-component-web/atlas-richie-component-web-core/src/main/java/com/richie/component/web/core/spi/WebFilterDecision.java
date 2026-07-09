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
package com.richie.component.web.core.spi;

/**
 * 拦截器决策事件载荷（见 README.md §3.4）。
 * <p>
 * 当某个拦截器做出 deny / short-circuit 决策时发布此事件，供下游 HookBus 订阅并通知业务方。
 *
 * <h2>关键非琐碎细节</h2>
 * <ul>
 *   <li><strong>HookBus.publish 永远不被短路跳过</strong>：即使请求被拒绝，决策事件也必须发出（§3.4）</li>
 *   <li><strong>多个 deny 决策同时发生时</strong>：以"首先触发的那个"的响应为准（§3.4）—— 由发布顺序保证</li>
 *   <li><strong>被拒绝的请求不计 OTEL success metric</strong>：{@link #status()} 为 4xx/5xx 时由
 *       {@code OtelSpanInterceptor} 标记为 error span</li>
 * </ul>
 *
 * @param interceptor 触发决策的拦截器名（{@code SimpleName}），便于日志检索；如 {@code "RateLimitInterceptor"}
 * @param key 决策键（rate-limit / circuit-breaker 使用的 key），可空
 * @param status 决策 HTTP 状态码（如 429 / 503）
 * @param reason 决策原因（自由文本，brief），如 {@code "rate_limit.exceeded"} / {@code "circuit_breaker.open"}
 * @author richie696
 * @since 2026-07
 */
public record WebFilterDecision(
        String interceptor,
        String key,
        int status,
        String reason) {

    /**
     * 限流拒绝事件的便捷工厂。
     */
    public static WebFilterDecision rateLimitDeny(String interceptor, String key, int status) {
        return new WebFilterDecision(interceptor, key, status, "rate_limit.exceeded");
    }

    /**
     * 熔断拒绝事件的便捷工厂。
     */
    public static WebFilterDecision circuitBreakerDeny(String interceptor, String key, int status) {
        return new WebFilterDecision(interceptor, key, status, "circuit_breaker.open");
    }

    /**
     * 是否为 deny 决策（4xx / 5xx）。
     */
    public boolean isDeny() {
        return status >= 400;
    }
}