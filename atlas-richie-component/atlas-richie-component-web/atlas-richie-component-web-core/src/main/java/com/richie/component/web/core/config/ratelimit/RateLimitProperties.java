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
package com.richie.component.web.core.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务接口级限流拦截器配置（前缀：{@code platform.component.web.rate-limit}，README.md §4.1）。
 * <p>
 * 控制 {@link com.richie.component.web.core.interceptor.RateLimitInterceptor} 的开关 / 阈值 / 兜底响应 / <strong>按接口粒度</strong>的限流配置。
 *
 * <h2>按接口粒度配置</h2>
 * <p>{@link #routes} 提供 path 维度的覆盖层：请求 path 命中某 Ant 模式，即用该路由专属的限流配置替换全局默认。
 * 每条 {@link RouteConfig} 支持独立的 {@code permitsPerSecond} / {@code denyStatus/Code/Msg/Headers}。
 *
 * <h2>routes 配置结构（List 形式）</h2>
 * <p>历史上曾用 {@code Map<String, RouteConfig>} 形式，但 Spring Boot 的 {@code @ConfigurationProperties}
 * Map 绑定走 "relaxed binding" 会<strong>剥离</strong> key 中的 {@code /} 与 {@code *}（{@code "/payments/**"} → {@code "payments"}），
 * 导致 AntPathMatcher 永远匹配不到。改用 {@link List} + 显式 {@link RouteConfig#getPattern() pattern} 字段解决此问题：
 * <pre>{@code
 * routes:
 *   - pattern: "/orders/**"
 *     permits-per-second: 2
 *     deny-code: ORDER_RATE_LIMITED
 *     deny-msg: "下单过快"
 * }</pre>
 *
 * <h2>匹配优先级</h2>
 * <ol>
 *   <li>精确 path 命中 {@link #routes}（pattern 与 path 完全相等）</li>
 *   <li>Ant 模式命中 {@link #routes}（按 routes 列表顺序，先到先得）</li>
 *   <li>全局 {@link #permitsPerSecond} / {@link #denyStatus}/{@link #denyCode}/{@link #denyMsg}/{@link #denyHeaders}</li>
 * </ol>
 *
 * <h2 style="color:#c00">职责定位（绝对断言）</h2>
 * <p>本子域<strong>仅承载业务接口级流量整形职责</strong>，<strong>永远不承担反压保护职责</strong>。
 * <ul>
 *   <li><strong>反压保护</strong>（防止上游流量把 Servlet 容器线程池打爆）必须由容器线程池<strong>之前</strong>的层处理：
 *       <ul>
 *         <li>部署 gateway 时：atlas-richie-gateway-service 端 {@code SecurityFilter}（IP / UA 频控）</li>
 *         <li><strong>不部署 gateway 时</strong>：<strong>Sentinel MVC 适配器</strong>（直接在 jetty / tomcat
 *             容器线程池侧保护），否则容器先被打爆</li>
 *       </ul>
 *   </li>
 *   <li><strong>为什么不在这里做反压？</strong>本组件拦截器运行在 Servlet 容器线程池<strong>之后</strong>——
 *       大流量进入时容器已经先挂，运行在后面的限流没机会救场。</li>
 *   <li><strong>本子域的正确用法</strong>：按 {@link #routes} path 维度配置业务规则
 *       （VIP 客户不限流 / 普通用户 5 req/s / 敏感接口 1 req/s 等），属于业务规则层的差异化流量整形，
 *       与反压防护正交、各管一段。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.rate-limit")
public class RateLimitProperties {

    /**
     * 是否启用限流拦截器；默认 true。
     */
    private boolean enabled = true;

    /**
     * 全局每秒令牌数（未命中 routes 时生效）。默认 50。
     */
    private int permitsPerSecond = 50;

    /**
     * 限流兜底 HTTP 状态码；默认 429。
     */
    private Integer denyStatus = 429;

    /**
     * 限流兜底业务 code（{@code ApiResult.code} 字段）。
     */
    private String denyCode = "RATE_LIMITED";

    /**
     * 限流兜底业务 msg（{@code ApiResult.msg} 字段），支持占位符 {@code {key}}（自动替换为 clientKey）。
     */
    private String denyMsg = "请求过于频繁，请稍后再试 (key={key})";

    /**
     * 限流兜底响应附加 header（系统级），与 ApiResult body 解耦。
     */
    private Map<String, String> denyHeaders = new HashMap<>();

    /**
     * 按接口粒度覆盖限流配置（<strong>List 形式</strong>）。每条 {@link RouteConfig} 自带
     * {@link RouteConfig#getPattern() pattern} 字段，避免 Spring Boot relaxed binding 剥离 path 字符。
     * 命中顺序：精确匹配优先，再 Ant 通配，<strong>同优先级按 List 顺序，先到先得</strong>。
     */
    private List<RouteConfig> routes = new ArrayList<>();

    /**
     * 按接口粒度的限流配置（与全局字段同语义，命中时覆盖全局）。
     */
    @Data
    public static class RouteConfig {
        /**
         * Ant 风格路径（如 {@code /api/v1/orders} 或 {@code /api/v1/orders/**}），
         * 必填字段。匹配方式：精确路径相等 → Ant 通配（{@code **} / {@code *} / {@code ?}）。
         */
        private String pattern;
        /**
         * 该路由每秒令牌数。
         */
        private int permitsPerSecond = 50;
        /**
         * 该路由限流兜底 HTTP 状态码；未设置时沿用全局 {@link RateLimitProperties#getDenyStatus()}。
         */
        private Integer denyStatus;
        /**
         * 该路由限流兜底业务 code；未设置时沿用全局 {@link RateLimitProperties#getDenyCode()}。
         */
        private String denyCode;
        /**
         * 该路由限流兜底业务 msg；未设置时沿用全局 {@link RateLimitProperties#getDenyMsg()}。
         */
        private String denyMsg;
        /**
         * 该路由限流兜底响应附加 header；未设置时沿用全局 {@link RateLimitProperties#getDenyHeaders()}。
         */
        private Map<String, String> denyHeaders;
    }
}