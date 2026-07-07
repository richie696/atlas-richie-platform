package com.richie.component.web.jetty.handler;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.util.UUID;

/**
 * Trace ID 注入 Handler（Jetty 级别，比 Spring Interceptor 早 5-10ms 介入）。
 *
 * <p>Phase 3: 在 Jetty 容器层提取或生成 trace ID，写入 {@link org.slf4j.MDC} 与请求
 * {@link Request#setAttribute(String, Object)}，供下游业务逻辑（如日志、响应 header）使用。</p>
 *
 * <p>Jetty 12 适配：继承 {@link Handler.Wrapper} 而非旧的 {@code AbstractHandler}，
 * {@link #handle(Request, Response, Callback)} 返回 {@code boolean}，
 * 使用 {@link Request#getHeaders()} 读取请求头，
 * 使用 {@link Response#getHeaders()} 写入响应头。</p>
 *
 * <h2>行为</h2>
 * <ol>
 *   <li>读取请求 header（默认 {@code X-Trace-Id}）</li>
 *   <li>若 header 缺失且 {@code generateIfMissing=true}，生成 UUID 填入</li>
 *   <li>写入 {@link org.slf4j.MDC}（键 = {@code trace_id}）</li>
 *   <li>写入请求 attribute（供下游 Filter / Interceptor 读取）</li>
 *   <li>回写响应 header（便于客户端透传）</li>
 * </ol>
 *
 * @author richie696
 * @since 1.0.0
 */
public class TraceIdInjectHandler extends Handler.Wrapper {

    public static final String TRACE_ID_MDC_KEY = "trace_id";
    public static final String TRACE_ID_REQUEST_ATTR = "trace_id";
    public static final String DEFAULT_HEADER = "X-Trace-Id";

    private final String headerName;
    private final boolean generateIfMissing;

    public TraceIdInjectHandler(String headerName, boolean generateIfMissing) {
        this.headerName = (headerName == null || headerName.isBlank()) ? DEFAULT_HEADER : headerName;
        this.generateIfMissing = generateIfMissing;
    }

    @Override
    public boolean handle(Request request, Response response, org.eclipse.jetty.util.Callback callback) throws Exception {
        String traceId = request.getHeaders().get(headerName);
        if (traceId == null || traceId.isBlank()) {
            if (generateIfMissing) {
                traceId = UUID.randomUUID().toString();
            } else {
                traceId = "n/a";
            }
        }
        // 写入 MDC（如果 SLF4J 在 classpath）
        try {
            org.slf4j.MDC.put(TRACE_ID_MDC_KEY, traceId);
        } catch (Throwable ignored) {
            // MDC may not be available in all contexts
        }
        // 写入请求 attribute 供业务代码使用
        request.setAttribute(TRACE_ID_REQUEST_ATTR, traceId);
        // 回写响应 header
        response.getHeaders().put(headerName, traceId);
        // 继续处理链
        return super.handle(request, response, callback);
    }
}
