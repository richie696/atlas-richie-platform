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
package com.richie.component.web.core.servlet;

import com.richie.component.web.core.hook.DefaultHookBus;
import com.richie.component.web.core.hook.HookBus;
import com.richie.component.web.core.hook.RequestCompletedEvent;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨容器适配层入口：{@code jakarta.servlet.Filter} → {@link WebRequestContext} → 拦截器链驱动。
 * <p>
 * R1 决议（README.md §3.3）：跨容器决策统一走 SPI 拦截器链，本类为唯一 servlet 适配点。
 * 任何 web 容器（Tomcat / Jetty / Undertow）只要实现了 {@code jakarta.servlet} 规范，
 * 都通过本 Filter 完成拦截。{@code web-tomcat} / {@code web-jetty} 等子模块不重复实现适配逻辑。
 *
 * <h2>处理流程</h2>
 * <pre>
 *   Filter.doFilter(req, resp, chain)
 *       ↓
 *   buildContext(req, resp)         → WebRequestContext
 *       ↓
 *   new DefaultWebInterceptorChain(interceptors).proceed(ctx)
 *       ↓
 *   拦截器按顺序执行：业务方可调 ctx.markShortCircuit(...) 或抛异常
 *       ↓
 *   ┌─── 短路 ───→ writeShortCircuit(resp, ctx) → 不调 chain.doFilter
 *   └─── 正常 ───→ chain.doFilter(req, resp)    → 进入 Spring Dispatcher
 * </pre>
 *
 * <h2>异常处理</h2>
 * <ul>
 *   <li>拦截器抛异常 → 包装为 {@link ServletException} 抛出，容器按错误页处理</li>
 *   <li>无论异常与否，{@link WebRequestContext#close()} 都会在 finally 触发（幂等）</li>
 * </ul>
 *
 * <h2>非 HTTP 请求</h2>
 * <p>{@link jakarta.servlet.Filter} 规范允许被非 HTTP servlet 调用（罕见）。遇此场景，本类直接
 * 放行（调 {@code chain.doFilter}），不构造 ctx、不驱动拦截器链。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class InterceptingFilter implements Filter {

    /**
     * 拦截器列表，注册顺序即执行顺序（见 README.md §4 SPI 层注释）。
     */
    private final List<WebInterceptor> interceptors;

    /**
     * 事件总线（README.md §4.5 HookBus）。为 {@code null} 时用 noop bus。
     */
    private final HookBus hookBus;

    public InterceptingFilter(List<WebInterceptor> interceptors) {
        this(interceptors, null);
    }

    public InterceptingFilter(List<WebInterceptor> interceptors, HookBus hookBus) {
        this.interceptors = List.copyOf(Objects.requireNonNull(interceptors, "interceptors must not be null"));
        this.hookBus = hookBus != null ? hookBus : new DefaultHookBus();
        log.info("InterceptingFilter initialized with {} interceptor(s): {}",
                this.interceptors.size(), namesOf(this.interceptors));
    }

    private static List<String> namesOf(List<WebInterceptor> interceptors) {
        List<String> names = new ArrayList<>(interceptors.size());
        for (WebInterceptor i : interceptors) {
            names.add(i.getClass().getSimpleName());
        }
        return names;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpReq)
                || !(response instanceof HttpServletResponse httpResp)) {
            // 非 HTTP：直接放行（罕见，但 servlet 规范允许）
            chain.doFilter(request, response);
            return;
        }

        WebRequestContext ctx = buildContext(httpReq);
        WebInterceptorChain intChain = new DefaultWebInterceptorChain(interceptors);

        try {
            intChain.proceed(ctx);

            if (ctx.isShortCircuited()) {
                writeShortCircuit(httpResp, ctx);
                return;
            }

            // 未短路 → 进入 Spring Dispatcher
            chain.doFilter(request, response);
        } catch (Exception e) {
            ctx.setError(e);
            log.warn("InterceptingFilter intercepted exception: method={} path={} msg={}",
                    ctx.method(), ctx.path(), e.getMessage(), e);
            // 包装为 ServletException 抛出，由容器按错误页处理（不直接写 5xx body —— 让容器统一渲染）
            throw new ServletException(e);
        } finally {
            try {
                ctx.close();
            } catch (RuntimeException closeEx) {
                // close 必须不抛 —— 但若实现有 bug 抛了，记录后吞掉，避免遮盖主异常
                log.warn("WebRequestContext.close() threw: {}", closeEx.getMessage(), closeEx);
            }
            try {
                hookBus.publish(RequestCompletedEvent.of(ctx, System.nanoTime()));
            } catch (RuntimeException hookEx) {
                log.warn("HookBus.publish threw: {}", hookEx.getMessage(), hookEx);
            }
        }
    }

    // ───────────────────────── 内部：构造 ctx ─────────────────────────

    private static WebRequestContext buildContext(HttpServletRequest req) {
        Map<String, List<String>> headers = collectHeaders(req);
        Map<String, List<String>> queryParams = collectQueryParams(req);
        return new MutableWebRequestContext(
                req.getMethod(),
                req.getRequestURI(),
                headers,
                queryParams);
    }

    private static Map<String, List<String>> collectHeaders(HttpServletRequest req) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = new ArrayList<>();
            Enumeration<String> v = req.getHeaders(name);
            while (v.hasMoreElements()) {
                values.add(v.nextElement());
            }
            headers.put(name, values);
        }
        return headers;
    }

    private static Map<String, List<String>> collectQueryParams(HttpServletRequest req) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        Enumeration<String> names = req.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String[] values = req.getParameterValues(name);
            params.put(name, values == null ? List.of() : List.of(values));
        }
        return params;
    }

    // ───────────────────────── 内部：写短路响应 ─────────────────────────

    private static void writeShortCircuit(HttpServletResponse resp, WebRequestContext ctx) throws IOException {
        resp.setStatus(ctx.responseStatus());
        Map<String, String> respHeaders = ctx.responseHeaders();
        for (Map.Entry<String, String> e : respHeaders.entrySet()) {
            resp.setHeader(e.getKey(), e.getValue());
        }
        String body = ctx.shortCircuitBody();
        if (body != null) {
            // 仅当 body 非空才设置 content-type —— 业务拦截器可只写 header 而不写 body
            if (!respHeaders.containsKey("Content-Type")) {
                resp.setContentType("application/json;charset=UTF-8");
            }
            resp.getWriter().write(body);
            resp.getWriter().flush();
        }
    }
}