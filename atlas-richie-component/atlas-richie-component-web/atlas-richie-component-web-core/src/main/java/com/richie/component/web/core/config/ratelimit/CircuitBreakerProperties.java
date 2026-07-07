package com.richie.component.web.core.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务接口级熔断拦截器配置（前缀：{@code platform.component.web.circuit-breaker}，README.md §4.2）。
 * <p>
 * 控制 {@link com.richie.component.web.core.interceptor.CircuitBreakerInterceptor} 的开关 / 阈值 / 兜底响应 / <strong>按被保护资源粒度</strong>的熔断配置。
 *
 * <h2>按被保护资源粒度配置</h2>
 * <p>{@link #routes} 提供 path 维度的覆盖层：CB key = matchedPattern（同一 path 所有 clientKey 共享 CB 状态机）。
 * 每条 {@link RouteConfig} 支持独立的 {@code failureRateThreshold} / {@code slidingWindowDuration} / {@code waitDurationInOpenState}。
 *
 * <h2>routes 配置结构（List 形式）</h2>
 * <p>历史上曾用 {@code Map<String, RouteConfig>} 形式，但 Spring Boot 的 {@code @ConfigurationProperties}
 * Map 绑定走 "relaxed binding" 会<strong>剥离</strong> key 中的 {@code /} 与 {@code *}（{@code "/payments/**"} → {@code "payments"}），
 * 导致 AntPathMatcher 永远匹配不到。改用 {@link List} + 显式 {@link RouteConfig#getPattern() pattern} 字段解决此问题：
 * <pre>{@code
 * routes:
 *   - pattern: "/payments/**"
 *     failure-rate-threshold: 30
 *     sliding-window-duration: 10
 *     deny-code: PAYMENT_CB_OPEN
 *     deny-msg: "支付服务熔断"
 * }</pre>
 *
 * <h2>匹配优先级</h2>
 * <ol>
 *   <li>精确 path 命中 {@link #routes}（pattern 与 path 完全相等）</li>
 *   <li>Ant 模式命中 {@link #routes}（按 routes 列表顺序，先到先得）</li>
 *   <li>全局 {@link #failureRateThreshold}/{@link #slidingWindowDuration}/{@link #waitDurationInOpenState}</li>
 * </ol>
 *
 * <h2 style="color:#c00">职责定位（绝对断言）</h2>
 * <p>本子域<strong>仅承载业务接口级熔断职责</strong>，<strong>永远不承担反压保护职责</strong>。
 * <ul>
 *   <li><strong>反压保护</strong>必须由容器线程池<strong>之前</strong>的层处理：
 *       <ul>
 *         <li>部署 gateway 时：gateway {@code SecurityFilter}（IP / UA 维度熔断）</li>
 *         <li><strong>不部署 gateway 时</strong>：<strong>Sentinel MVC 适配器</strong>（容器线程池侧），
 *             否则容器先被打爆</li>
 *       </ul>
 *   </li>
 *   <li><strong>为什么不在这里做反压？</strong>运行在 Servlet 容器线程池<strong>之后</strong>——大流量进入时
 *       容器已经先挂，运行在后面的熔断器没机会救场。</li>
 *   <li><strong>本子域的正确用法</strong>：按 {@link #routes} path 维度隔离独立 CB 状态机
 *       （"订单查询接口"失败率 30% 熔断保护下游 DB，"用户查询"不受影响），属于<strong>下游业务资源保护</strong>。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.circuit-breaker")
public class CircuitBreakerProperties {

    /**
     * 是否启用熔断拦截器；默认 true。
     */
    private boolean enabled = true;

    /**
     * 全局失败率阈值（百分比，0-100）。默认 50。
     */
    private int failureRateThreshold = 50;

    /**
     * 全局滑动窗口大小。默认 10s。
     */
    private Duration slidingWindowDuration = Duration.ofSeconds(10);

    /**
     * 全局 OPEN → HALF_OPEN 等待时长。默认 30s。
     */
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);

    /**
     * 熔断兜底 HTTP 状态码；默认 503。
     */
    private Integer denyStatus = 503;

    /**
     * 熔断兜底业务 code（{@code ApiResult.code} 字段）。
     */
    private String denyCode = "CIRCUIT_OPEN";

    /**
     * 熔断兜底业务 msg（{@code ApiResult.msg} 字段），支持占位符 {@code {key}}（自动替换为 clientKey）。
     */
    private String denyMsg = "服务暂不可用，请稍后重试 (key={key})";

    /**
     * 熔断兜底响应附加 header（系统级），与 ApiResult body 解耦。
     */
    private Map<String, String> denyHeaders = new HashMap<>();

    /**
     * 按被保护资源粒度覆盖熔断配置（<strong>List 形式</strong>）。每条 {@link RouteConfig} 自带
     * {@link RouteConfig#getPattern() pattern} 字段，避免 Spring Boot relaxed binding 剥离 path 字符。
     * 命中顺序：精确匹配优先，再 Ant 通配，<strong>同优先级按 List 顺序，先到先得</strong>。
     */
    private List<RouteConfig> routes = new ArrayList<>();

    /**
     * 按被保护资源粒度的熔断配置（与全局字段同语义，命中时覆盖全局）。
     */
    @Data
    public static class RouteConfig {
        /**
         * Ant 风格路径（如 {@code /api/v1/orders} 或 {@code /api/v1/orders/**}），
         * 必填字段。匹配方式：精确路径相等 → Ant 通配（{@code **} / {@code *} / {@code ?}）。
         */
        private String pattern;
        /**
         * 该被保护资源失败率阈值（百分比，0-100）。
         */
        private int failureRateThreshold = 50;
        /**
         * 该被保护资源滑动窗口大小；未设置时沿用全局 {@link CircuitBreakerProperties#getSlidingWindowDuration()}。
         */
        private Duration slidingWindowDuration;
        /**
         * 该被保护资源 OPEN → HALF_OPEN 等待时长；未设置时沿用全局 {@link CircuitBreakerProperties#getWaitDurationInOpenState()}。
         */
        private Duration waitDurationInOpenState;
        /**
         * 该被保护资源熔断兜底 HTTP 状态码；未设置时沿用全局。
         */
        private Integer denyStatus;
        /**
         * 该被保护资源熔断兜底业务 code；未设置时沿用全局。
         */
        private String denyCode;
        /**
         * 该被保护资源熔断兜底业务 msg；未设置时沿用全局。
         */
        private String denyMsg;
        /**
         * 该被保护资源熔断兜底响应附加 header；未设置时沿用全局。
         */
        private Map<String, String> denyHeaders;
    }
}