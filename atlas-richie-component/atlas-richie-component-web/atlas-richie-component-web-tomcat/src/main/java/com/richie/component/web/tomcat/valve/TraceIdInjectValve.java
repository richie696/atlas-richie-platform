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
package com.richie.component.web.tomcat.valve;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * Trace ID 注入 Valve（Tomcat 级别，比 Spring Interceptor 早 5-10ms 介入）。
 *
 * <p>Phase 3 — 在 Tomcat 容器层提取或生成 trace ID，写入 {@link org.slf4j.MDC} 与
 * {@link Request#setAttribute(String, Object)}，供下游业务逻辑使用。</p>
 *
 * <p>继承 {@link ValveBase}：覆盖 {@link #invoke(Request, Response)} 签名 — 必须
 * 使用 Tomcat 内部 {@code org.apache.catalina.connector.Request} 而非
 * {@code jakarta.servlet.http.HttpServletRequest}。response 可桥接到 jakarta
 * 形式以便写响应头。</p>
 *
 * <h2>行为</h2>
 * <ol>
 *   <li>读取请求 header（默认 {@code X-Trace-Id}）</li>
 *   <li>若缺失且 {@code generateIfMissing=true}，生成 UUID</li>
 *   <li>写入 {@link org.slf4j.MDC}（键 = {@code trace_id}）</li>
 *   <li>写入请求 attribute</li>
 *   <li>回写响应 header（透传）</li>
 * </ol>
 *
 * @author richie696
 * @since 1.0.0
 */
public class TraceIdInjectValve extends ValveBase {

    private static final Logger log = LoggerFactory.getLogger(TraceIdInjectValve.class);

    public static final String TRACE_ID_MDC_KEY = "trace_id";
    public static final String TRACE_ID_REQUEST_ATTR = "trace_id";
    public static final String DEFAULT_HEADER = "X-Trace-Id";

    private final String headerName;
    private final boolean generateIfMissing;

    public TraceIdInjectValve(String headerName, boolean generateIfMissing) {
        this.headerName = (headerName == null || headerName.isBlank()) ? DEFAULT_HEADER : headerName;
        this.generateIfMissing = generateIfMissing;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, jakarta.servlet.ServletException {
        try {
            String traceId = request.getHeader(headerName);
            if (traceId == null || traceId.isBlank()) {
                traceId = generateIfMissing ? UUID.randomUUID().toString() : "n/a";
            }
            try {
                org.slf4j.MDC.put(TRACE_ID_MDC_KEY, traceId);
            } catch (Throwable ignored) {
                // MDC may not be available in some contexts
            }
            request.setAttribute(TRACE_ID_REQUEST_ATTR, traceId);
            // Tomcat Response 是 servlet Response 包装，可直接设 header
            if (response instanceof HttpServletResponse hsResponse) {
                hsResponse.setHeader(headerName, traceId);
            }
        } catch (Exception e) {
            log.warn("Failed to inject trace ID", e);
        }
        Valve next = getNext();
        if (next != null) {
            next.invoke(request, response);
        }
    }

    public String getHeaderName() {
        return headerName;
    }

    public boolean isGenerateIfMissing() {
        return generateIfMissing;
    }
}
