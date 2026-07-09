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
package com.richie.component.web.core.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 容器无关的请求上下文（SPI 层）。
 * <p>
 * 适配层（{@code InterceptingFilter}）将各容器的原生 {@code ServletRequest/Response} 适配为本接口，
 * 拦截器链中所有跨容器决策（限流 / 熔断 / OTEL / HangDetection / 业务降级）只面向本接口编程。
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><strong>只读部分</strong>：method / path / header / query / pathVariables —— 一旦装配不可变。</li>
 *   <li><strong>可写部分</strong>：attributes / responseStatus / responseHeaders / shortCircuit —— 拦截器可改。</li>
 *   <li><strong>客户端 key</strong>：由 KeyResolver 解析后写入 {@link #clientKey()}，供 {@code RateLimitInterceptor}、
 *       {@code CircuitBreakerInterceptor} 使用（见 README.md §4.1 / §4.2）。</li>
 *   <li><strong>traceId</strong>：由 {@code OtelSpanInterceptor} 或 {@code RequestLifecycleHookInterceptor} 写入，
 *       后续拦截器可在 attributes 中读取并写入响应头。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <p>实现必须实现 {@link AutoCloseable}，在请求结束时（无论正常或异常）调用 {@link #close()}：
 * <ul>
 *   <li>取消已注册的 {@code ScheduledFuture}（HangDetection 见 §4.4）</li>
 *   <li>发布 {@code RequestCompleted} 事件</li>
 *   <li>释放底层资源</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public interface WebRequestContext extends AutoCloseable {

    // ───────────────────────── 只读：HTTP 入站 ─────────────────────────

    /**
     * HTTP 方法（大写），例如 {@code "GET"} / {@code "POST"}。
     * <p>对应 {@code jakarta.servlet.http.HttpServletRequest.getMethod()}。
     */
    String method();

    /**
     * 请求路径，不含 query string，例如 {@code "/api/v1/users/123"}。
     * <p>对应 {@code HttpServletRequest.getRequestURI()}。
     */
    String path();

    /**
     * 取指定 header 的第一个值；不存在返回 {@code null}。
     *
     * @param name header 名（大小写不敏感）
     */
    String header(String name);

    /**
     * 取指定 header 的所有值；不存在返回空列表（不为 {@code null}）。
     *
     * @param name header 名（大小写不敏感）
     */
    List<String> headers(String name);

    /**
     * 全部 header 名（与 {@link #headers(String)} 配合遍历用）。
     * <p>
     * 默认实现返回空集——为兼容既有调用方。完整实现（{@code MutableWebRequestContext} /
     * 后续 web-jetty 适配层）应返回全部 header 名称集合。调用方应兼容默认空集（按"无法检查"处理）。
     *
     * @return header 名集合（大小写不敏感），默认实现为空集
     */
    default java.util.Set<String> headerNames() {
        return java.util.Set.of();
    }

    /**
     * 取指定 query 参数的第一个值；不存在返回 {@code null}。
     */
    String queryParam(String name);

    /**
     * 全部 query 参数。
     */
    Map<String, List<String>> queryParams();

    /**
     * 路径变量（如 {@code /users/{id}} 解析出的 {@code id}）。
     * <p>在 A-1 阶段通常为空，由 web-tomcat / web-jetty 适配层在 dispatcher 之后回填。
     */
    Map<String, String> pathVariables();

    // ───────────────────────── 可写：跨拦截器共享数据 ─────────────────────────

    /**
     * 取 attribute；不存在返回 {@code null}。
     *
     * @param name attribute 名，业务方自定义
     */
    <T> T attribute(String name);

    /**
     * 设置 attribute，覆盖同名旧值，返回旧值（无旧值则返回 {@code null}）。
     */
    <T> T setAttribute(String name, T value);

    /**
     * 删除 attribute，返回旧值。
     */
    <T> T removeAttribute(String name);

    // ───────────────────────── 可写：响应出站 ─────────────────────────

    /**
     * 响应状态码。初始值 200；拦截器可改（如 429 / 503）。
     */
    int responseStatus();

    /**
     * 设置响应状态码。
     */
    void setResponseStatus(int status);

    /**
     * 全部响应头（只读视图，调用 {@link #addResponseHeader} 修改）。
     */
    Map<String, String> responseHeaders();

    /**
     * 追加响应头（同名校验由调用方决定；通常后写覆盖）。
     */
    void addResponseHeader(String name, String value);

    // ───────────────────────── 可写：客户端 key（供 RateLimit / CB 使用） ─────────────────────────

    /**
     * 客户端 key，由 KeyResolver 解析后写入。
     * <p>见 README.md §4.1 配置 {@code platform.component.web.rate-limit.key-resolver}。
     */
    String clientKey();

    /**
     * 设置客户端 key。无 key 时 RateLimit / CB 应直接拒绝（视为配置错误）。
     */
    void setClientKey(String key);

    // ───────────────────────── 可写：traceId ─────────────────────────

    /**
     * traceId，由 OtelSpanInterceptor 从上游 {@code traceparent} / {@code X-Request-Id} 解析或自生成。
     */
    String traceId();

    /**
     * 设置 traceId。
     */
    void setTraceId(String traceId);

    // ───────────────────────── 短路（deny 决策） ─────────────────────────

    /**
     * 是否已被某个拦截器短路（参见 §3.4 短路语义）。
     * <p>短路后即使后续拦截器在 chain 中被调用，也应检查此标志并直接返回。
     */
    boolean isShortCircuited();

    /**
     * 标记短路，写入响应状态码与 body 模板。调用后 {@link #isShortCircuited()} 返回 {@code true}。
     *
     * @param status 响应状态码（如 429 / 503）
     * @param body   响应 body 字符串（可能为 JSON；可包含占位符由适配层填充）
     */
    void markShortCircuit(int status, String body);

    /**
     * 短路 body 模板；无短路时返回 {@code null}。
     */
    String shortCircuitBody();

    // ───────────────────────── 错误状态 ─────────────────────────

    /**
     * 当前请求上发生的异常（若有）。
     * <p>若已存在异常，setter 应覆盖（而不是叠加）；用于业务异常 → publish(Exception) 场景。
     */
    Optional<Throwable> error();

    /**
     * 设置异常。
     */
    void setError(Throwable error);

    // ───────────────────────── 生命周期 ─────────────────────────

    /**
     * 请求起始纳秒（System.nanoTime()），用于 HangDetection 计算耗时。
     */
    long startNanos();

    /**
     * 关闭上下文：取消注册的 ScheduledFuture、发布 {@code RequestCompleted} 事件、释放资源。
     * <p>必须可重复调用，幂等。
     */
    @Override
    void close();
}