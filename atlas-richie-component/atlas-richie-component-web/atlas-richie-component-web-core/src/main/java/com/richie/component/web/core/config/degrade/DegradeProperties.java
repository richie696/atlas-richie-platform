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
package com.richie.component.web.core.config.degrade;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务接口级降级拦截器配置（前缀：{@code platform.component.web.degrade}，README.md §4.7）。
 * <p>
 * 控制 {@link DegradeInterceptor} 的开关 / 兜底响应 / <strong>按接口粒度</strong>的降级响应覆盖。
 *
 * <h2 style="color:#c00">职责定位（绝对断言）</h2>
 * <p>本子域<strong>仅承载业务接口级降级响应职责</strong>，<strong>永远不承担上游兜底职责</strong>。
 * <ul>
 *   <li><strong>上游兜底</strong>（上游不可达 / 超时的统一 fallback）必须由容器线程池<strong>之前</strong>的层处理：
 *       <ul>
 *         <li>部署 gateway 时：gateway {@code FallbackConfig}</li>
 *         <li><strong>不部署 gateway 时</strong>：<strong>Sentinel MVC 适配器</strong>（容器线程池侧）或
 *             Web 容器本身（tomcat / jetty 自带的 error page）</li>
 *       </ul>
 *   </li>
 *   <li><strong>为什么不在这里做上游兜底？</strong>运行在 Servlet 容器线程池<strong>之后</strong>——上游不可达时
 *       容器已经先报错/超时，运行在后面的降级拿不到完整上下文。</li>
 *   <li><strong>本子域的正确用法</strong>：按 {@link #routes} path 维度返回业务特定的 code/msg
 *       （"订单查询"返回 {@code ORDER_DEGRADED}，"用户查询"返回 {@code USER_DEGRADED}，
 *       或引用 {@code fallbackBean} 动态生成响应体），属于<strong>业务响应差异化</strong>。</li>
 * </ul>
 *
 * <h2>响应结构</h2>
 * <p>兜底响应复用统一 {@code ApiResult} 结构：HTTP 状态码由 {@link Fallback#getStatus()} 控制；
 * 业务层 {@code code} / {@code msg} 由 {@link Fallback#getCode()} / {@link Fallback#getMsg()} 控制；
 * {@link Fallback#getHeaders()} 仅用于系统级响应头，不在 body 中。
 *
 * <h2>按接口粒度配置</h2>
 * <p>{@link #routes} 提供 path 维度的覆盖层：请求 path 命中某 Ant 模式（如 {@code /api/v1/orders} 或
 * {@code /api/v1/orders/**}），即用该路由专属的降级响应替换全局兜底。每条 {@link RouteFallback} 支持：
 * <ul>
 *   <li>静态配置 {@code status/code/msg/headers}（最简）</li>
 *   <li>引用 {@code fallbackBean}/{@code fallbackMethod} 动态生成响应体（反射调用 Spring Bean
 *       方法，参数为 {@link com.richie.component.web.core.spi.WebRequestContext}，返回
 *       {@link com.richie.contract.model.ApiResult} 或 String JSON）</li>
 * </ul>
 *
 * <h2>匹配优先级</h2>
 * <ol>
 *   <li>精确 path 命中 {@link #routes}</li>
 *   <li>Ant 模式命中 {@link #routes}</li>
 *   <li>全局 {@link #fallback}</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.degrade")
public class DegradeProperties {

    /**
     * 是否启用降级拦截器；默认 true。
     */
    private boolean enabled = true;

    /**
     * 无策略命中时的兜底响应（防止"业务异常但无降级策略"导致裸 500）。
     */
    private Fallback fallback = new Fallback();

    /**
     * 按接口粒度覆盖兜底响应。Key 为 Ant 风格路径（如 {@code /api/v1/orders} 或 {@code /api/v1/orders/**}）；
     * Value 为该 path 专属的降级响应。命中顺序：精确匹配优先，再 Ant 通配。
     */
    private Map<String, RouteFallback> routes = new HashMap<>();

    /**
     * 兜底响应（默认 503）。
     */
    @Data
    public static class Fallback {
        /**
         * 兜底 HTTP 状态码；默认 503。
         */
        private int status = 503;
        /**
         * 兜底响应业务 code（{@code ApiResult.code} 字段）。
         */
        private String code = "DEGRADED";
        /**
         * 兜底响应业务 msg（{@code ApiResult.msg} 字段），支持占位符
         * {@code {reason}}（自动替换为 trigger 名小写，如 {@code exception}）。
         */
        private String msg = "服务降级中 ({reason})";
        /**
         * 兜底响应附加 header（系统级），与 ApiResult body 解耦。
         */
        private Map<String, String> headers = new HashMap<>();
    }

    /**
     * 按接口粒度的降级响应配置。
     * <p>
     * 命中 {@link DegradeProperties#getRoutes()} 中对应 path pattern 的请求，使用本配置生成降级响应。
     * <ul>
     *   <li>静态路径：直接用 {@link #status}/{@link #code}/{@link #msg} + {@link #headers}，与全局 fallback 同语义</li>
     *   <li>动态路径：指定 {@link #fallbackBean} + {@link #fallbackMethod}，反射调用 Bean 方法生成响应体；
     *       此时 {@link #status} 与 {@link #headers} 仍生效，{@link #code}/{@link #msg} 由 Bean 方法返回的
     *       {@code ApiResult} 决定</li>
     * </ul>
     */
    @Data
    public static class RouteFallback {
        /**
         * 降级 HTTP 状态码；默认 503。
         */
        private int status = 503;
        /**
         * 降级响应业务 code（静态路径生效）。
         */
        private String code = "DEGRADED";
        /**
         * 降级响应业务 msg（静态路径生效，支持占位符 {@code {reason}}）。
         */
        private String msg = "服务降级中 ({reason})";
        /**
         * 降级响应附加 header（系统级）。
         */
        private Map<String, String> headers = new HashMap<>();
        /**
         * 可选：动态降级 Bean 的 Spring bean name（不指定则走静态配置）。
         */
        private String fallbackBean;
        /**
         * 可选：Bean 上的降级方法名。方法签名：{@code public Object methodName(WebRequestContext ctx)}，
         * 返回 {@link com.richie.contract.model.ApiResult} 或 String JSON。
         */
        private String fallbackMethod;
    }
}