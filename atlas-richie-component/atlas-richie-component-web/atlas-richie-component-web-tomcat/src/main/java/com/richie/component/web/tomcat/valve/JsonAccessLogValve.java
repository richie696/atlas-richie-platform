package com.richie.component.web.tomcat.valve;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON 格式的 Tomcat 访问日志 Valve。
 *
 * <p>Phase 3: 结构化访问日志。每行一条 JSON 记录，字段固定，便于 ELK / Loki 摄取。
 * 相比 Tomcat 自带的 NCSA plain 格式，更易于日志聚合系统解析。</p>
 *
 * <p>继承 {@link ValveBase} — 标准 Valve 抽象层。计时逻辑包裹在
 * {@code getNext().invoke()} 前后，记录开始 / 结束时间再序列化输出。</p>
 *
 * <h2>输出字段</h2>
 * <pre>{@code
 * {
 *   "ts": "2026-07-04T12:34:56.789Z",
 *   "method": "GET",
 *   "uri": "/api/users/123",
 *   "status": 200,
 *   "duration_ms": 12,
 *   "remote": "192.168.1.10:54321",
 *   "ua": "curl/7.85.0",
 *   "trace_id": "abc-123-def-456"
 * }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public class JsonAccessLogValve extends ValveBase {

    private final AtomicLong count = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> statusCount = new ConcurrentHashMap<>();
    private final OutputWriter writer;

    public JsonAccessLogValve(OutputWriter writer) {
        this.writer = writer;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, jakarta.servlet.ServletException {
        long startNanos = System.nanoTime();
        try {
            Valve next = getNext();
            if (next != null) {
                next.invoke(request, response);
            }
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logAccess(request, response, durationMs);
        }
    }

    private void logAccess(Request request, Response response, long durationMs) {
        try {
            count.incrementAndGet();
            int status = response.getStatus();
            statusCount.computeIfAbsent(String.valueOf(status), k -> new AtomicLong(0)).incrementAndGet();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", Instant.now().toString());
            entry.put("method", request.getMethod());
            entry.put("uri", request.getRequestURI());
            entry.put("status", status);
            entry.put("duration_ms", durationMs);
            entry.put("remote", request.getRemoteAddr() + ":" + request.getRemotePort());
            entry.put("ua", request.getHeader("User-Agent"));
            entry.put("trace_id", request.getHeader("X-Trace-Id"));

            writer.writeLine(serializeJson(entry));
        } catch (Exception ignored) {
            // access log must not break the request flow
        }
    }

    private String serializeJson(Map<String, Object> entry) {
        StringBuilder sb = new StringBuilder(192);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : entry.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public long getTotalCount() {
        return count.get();
    }

    public long getStatusCount(String status) {
        AtomicLong c = statusCount.get(status);
        return c == null ? 0 : c.get();
    }

    /**
     * Output writer — 抽象输出目的地（stdout / file / Kafka）。
     */
    public interface OutputWriter {
        void writeLine(String line);
    }
}
