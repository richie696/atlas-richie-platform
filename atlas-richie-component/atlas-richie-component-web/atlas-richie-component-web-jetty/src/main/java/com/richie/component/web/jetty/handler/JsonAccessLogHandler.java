package com.richie.component.web.jetty.handler;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON 格式的 Jetty 访问日志 Handler。
 *
 * <p>Phase 3: 结构化访问日志。输出 JSON 到标准输出或文件（取决于配置），比 NCSA plain 格式
 * 更易于 ELK / Loki / Splunk 摄取。每个请求一条 JSON 行，字段固定。</p>
 *
 * <p>Jetty 12 适配：继承 {@link Handler.Wrapper} 而非旧的 {@code AbstractHandler}，
 * {@link #handle(Request, Response, Callback)} 返回 {@code boolean} 表示请求是否被处理，
 * 使用 {@link Request#getHttpURI()} 取 URI 路径，{@link Request#getHeaders()} 取请求头，
 * {@link Request#getRemoteAddr(Request)} 静态方法取远程 IP。</p>
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
public class JsonAccessLogHandler extends Handler.Wrapper {

    private final AtomicLong count = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> statusCount = new ConcurrentHashMap<>();
    private final OutputWriter writer;

    public JsonAccessLogHandler(OutputWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean handle(Request request, Response response, org.eclipse.jetty.util.Callback callback) throws Exception {
        long startNanos = System.nanoTime();
        boolean handled = false;
        try {
            handled = super.handle(request, response, callback);
            return handled;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logRequest(request, response, durationMs);
        }
    }

    private void logRequest(Request request, Response response, long durationMs) {
        try {
            count.incrementAndGet();
            int status = response.getStatus();
            statusCount.computeIfAbsent(String.valueOf(status), k -> new AtomicLong(0)).incrementAndGet();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", java.time.Instant.now().toString());
            entry.put("method", request.getMethod());
            entry.put("uri", request.getHttpURI().getPath());
            entry.put("status", status);
            entry.put("duration_ms", durationMs);
            entry.put("remote", Request.getRemoteAddr(request) + ":" + Request.getRemotePort(request));
            entry.put("ua", request.getHeaders().get("User-Agent"));
            entry.put("trace_id", request.getHeaders().get("X-Trace-Id"));

            writer.writeLine(serializeJson(entry));
        } catch (Exception ignored) {
            // access log must not break the request flow
        }
    }

    private String serializeJson(Map<String, Object> entry) {
        // Simple JSON serializer without bringing in a JSON library
        StringBuilder sb = new StringBuilder(128);
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
     * Output writer interface — allows the handler to be agnostic about output destination
     * (stdout, file, Kafka, etc.). Default implementation writes to stdout.
     */
    public interface OutputWriter {
        void writeLine(String line);
    }
}
