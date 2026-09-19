/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.CorrelationIds;
import org.slf4j.MDC;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把当前 OTel Context 和业务 request_id 映射到日志 MDC。
 *
 * <p>该类只负责字段生命周期，不负责 JSON 编码。应用可以继续使用现有 Logback
 * encoder，只需输出 MDC 字段即可。</p>
 */
public final class ObservabilityMdc {

    private static final int MAX_VALUE_LENGTH = 512;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "cookie", "password", "secret", "prompt",
            "token", "access_token", "refresh_token", "tool.arguments");

    private ObservabilityMdc() {
    }

    public static Scope install(CorrelationIds ids) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (ids != null) {
            putIfPresent("trace_id", ids.traceId());
            putIfPresent("span_id", ids.spanId());
            putIfPresent("request_id", ids.requestId());
        }
        MDC.put("stage", "inbound");
        return new Scope(previous);
    }

    public static void put(String key, String value) {
        putIfPresent(key, safeValue(key, value));
    }

    private static void putIfPresent(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    static String safeValue(String key, String value) {
        if (value == null) {
            return null;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(normalizedKey)
                || normalizedKey.contains("authorization")
                || normalizedKey.contains("cookie")
                || normalizedKey.contains("password")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("token")
                || normalizedKey.contains("prompt")) {
            return "[REDACTED]";
        }
        String safe = value.replace('\n', '_').replace('\r', '_');
        return safe.length() <= MAX_VALUE_LENGTH
                ? safe
                : safe.substring(0, MAX_VALUE_LENGTH);
    }

    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previous;
        private boolean closed;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            MDC.clear();
            if (previous != null && !previous.isEmpty()) {
                MDC.setContextMap(previous);
            }
        }
    }
}
