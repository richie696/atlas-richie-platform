package com.richie.component.web.core.tracing;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Trace ID 解析器（README.md §4.3 / §6 R3）。
 * <p>
 * 解析顺序：
 * <ol>
 *   <li>W3C {@code traceparent} header（格式 {@code 00-<trace-id>-<parent-id>-<flags>}）</li>
 *   <li>{@code X-Request-Id} header（兜底）</li>
 *   <li>自生成（UUID v4）</li>
 * </ol>
 *
 * <h2>OTel SDK 状态</h2>
 * <p>本类<strong>不</strong>依赖 {@code opentelemetry-api} SDK——只解析 W3C 标准
 * traceparent 字符串。SDK 是否启用由业务方决定（{@code §6 R3} 倾向 optional）。
 *
 * @author richie696
 * @since 2026-07
 */
public final class TraceIdParser {

    /** W3C traceparent header 名。 */
    public static final String TRACEPARENT_HEADER = "traceparent";

    /** 业务侧常用 Request ID header。 */
    public static final String X_REQUEST_ID_HEADER = "X-Request-Id";

    /** 响应头：让下游链路可关联。 */
    public static final String RESPONSE_TRACE_ID_HEADER = "X-Trace-Id";

    /** traceparent 协议版本（当前固定 00）。 */
    private static final String TRACEPARENT_VERSION = "00";

    /** trace-id 长度（W3C 标准 16 字节 = 32 hex char）。 */
    private static final int TRACE_ID_LENGTH = 32;

    private static final SecureRandom RNG = new SecureRandom();

    private TraceIdParser() {
    }

    /**
     * 从上游 header 解析或自生成 trace ID。
     *
     * @param traceparentValue {@code traceparent} header 值（可为 null）
     * @param requestIdValue   {@code X-Request-Id} header 值（可为 null）
     * @return 32-hex-char trace ID，永不为 null
     */
    public static String resolve(String traceparentValue, String requestIdValue) {
        if (traceparentValue != null && !traceparentValue.isBlank()) {
            String parsed = parseTraceparent(traceparentValue);
            if (parsed != null) {
                return parsed;
            }
        }
        if (requestIdValue != null && !requestIdValue.isBlank()) {
            String trimmed = requestIdValue.trim();
            if (isValidTraceId(trimmed)) {
                return trimmed;
            }
            return padOrGenerate(trimmed);
        }
        return generateTraceId();
    }

    static String parseTraceparent(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length < 2) {
            return null;
        }
        if (!TRACEPARENT_VERSION.equals(parts[0])) {
            return null;
        }
        String traceId = parts[1];
        if (!isValidTraceId(traceId)) {
            return null;
        }
        return traceId.toLowerCase();
    }

    static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }

    private static String padOrGenerate(String candidate) {
        if (candidate.length() >= TRACE_ID_LENGTH) {
            return candidate.substring(0, TRACE_ID_LENGTH).toLowerCase();
        }
        return candidate.toLowerCase() + generateTraceId().substring(0, TRACE_ID_LENGTH - candidate.length());
    }

    private static boolean isValidTraceId(String s) {
        if (s == null || s.length() != TRACE_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}