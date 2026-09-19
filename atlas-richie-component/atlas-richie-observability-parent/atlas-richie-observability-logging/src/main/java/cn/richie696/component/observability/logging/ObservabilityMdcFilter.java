/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.CorrelationIds;
import cn.richie696.component.observability.core.ObservabilityContext;
import cn.richie696.component.observability.core.ObservabilityState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet 请求的 request_id、MDC 和响应头关联过滤器。
 *
 * <p>不创建 Server Span，避免与官方 OTel Servlet instrumentation 重复；当前 Span
 * 由 OTel instrumentation 提供，过滤器只读取它。</p>
 */
public final class ObservabilityMdcFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityMdcFilter.class);
    private static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
            "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final ObservabilityState state;

    public ObservabilityMdcFilter(ObservabilityState state) {
        this.state = state;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (state.disabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = safeRequestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try (var contextScope = ObservabilityContext.withRequestId(requestId);
             var mdcScope = ObservabilityMdc.install(CorrelationIds.current())) {
            ObservabilityMdc.put("operation", operation(request));
            long startedAt = System.nanoTime();
            try {
                filterChain.doFilter(request, response);
            } catch (ServletException | IOException | RuntimeException exception) {
                completeLog(response, startedAt, exception);
                throw exception;
            } catch (Error error) {
                completeLog(response, startedAt, error);
                throw error;
            }
            completeLog(response, startedAt, null);
        }
    }

    private static void completeLog(
            HttpServletResponse response,
            long startedAt,
            Throwable failure) {
        ObservabilityMdc.put("status", Integer.toString(response.getStatus()));
        ObservabilityMdc.put("duration_ms", Long.toString(
                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)));
        if (failure != null) {
            ObservabilityMdc.put("error.type", failure.getClass().getName());
            ObservabilityMdc.put("error.stage", "servlet.filter");
            LOGGER.warn("HTTP request completed with error {}", correlationFields());
        } else {
            LOGGER.info("HTTP request completed {}", correlationFields());
        }
    }

    private static String correlationFields() {
        CorrelationIds ids = CorrelationIds.current();
        return "request_id=" + safeLogValue(ids.requestId())
                + " trace_id=" + safeLogValue(ids.traceId())
                + " span_id=" + safeLogValue(ids.spanId());
    }

    private static String safeLogValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String operation(HttpServletRequest request) {
        Object pattern = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern instanceof String routePattern && !routePattern.isBlank()
                ? routePattern
                : request.getServletPath();
        if (route == null || route.isBlank()) {
            route = "<unknown>";
        }
        return request.getMethod() + " " + route;
    }

    static String safeRequestId(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_REQUEST_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-' || character == '_' || character == '.' || character == ':')) {
                return UUID.randomUUID().toString();
            }
        }
        return candidate;
    }
}
