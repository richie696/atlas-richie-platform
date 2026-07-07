package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.WebRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 默认 {@link WebRequestContext} 实现（纯 Java，可变）。
 * <p>
 * 适用场景：
 * <ul>
 *   <li>单元测试：直接构造，传入字段即可；</li>
 *   <li>web-tomcat / web-jetty 适配层：作为基类持有 ServletRequest/Response 引用，本类提供全部字段存取；</li>
 *   <li>非 servlet 场景（如 mock transport）：直接用本类。</li>
 * </ul>
 *
 * <h2>不可变部分</h2>
 * <p>构造时传入的 {@code method} / {@code path} / {@code headers} / {@code queryParams} 一旦构造不可变；
 * 业务方修改这些集合不影响本实例（已通过 {@link List#copyOf} 与 {@link Map#copyOf} 防御性拷贝）。
 *
 * <h2>线程安全</h2>
 * <p>本类<strong>非线程安全</strong>：每个请求一个实例。attributes / responseStatus / responseHeaders
 * 的并发访问由调用方保证（或在并发场景下用并发容器替换，本类为简化实现不做并发保护）。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class MutableWebRequestContext implements WebRequestContext {

    private final String method;
    private final String path;
    private final Map<String, List<String>> headers;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> pathVariables;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String> responseHeaders = new HashMap<>();
    private final long startNanos;

    private int responseStatus = 200;
    private String clientKey;
    private String traceId;
    private boolean shortCircuited;
    private String shortCircuitBody;
    private Throwable error;
    private boolean closed;

    public MutableWebRequestContext(
            String method,
            String path,
            Map<String, List<String>> headers,
            Map<String, List<String>> queryParams) {
        this(method, path, headers, queryParams, Map.of());
    }

    public MutableWebRequestContext(
            String method,
            String path,
            Map<String, List<String>> headers,
            Map<String, List<String>> queryParams,
            Map<String, String> pathVariables) {
        this.method = Objects.requireNonNull(method, "method must not be null").toUpperCase();
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.headers = copyMultiMap(headers);
        this.queryParams = copyMultiMap(queryParams);
        this.pathVariables = Map.copyOf(Objects.requireNonNull(pathVariables, "pathVariables must not be null"));
        this.startNanos = System.nanoTime();
    }

    private static Map<String, List<String>> copyMultiMap(Map<String, List<String>> src) {
        Objects.requireNonNull(src, "src must not be null");
        Map<String, List<String>> copy = new HashMap<>(src.size());
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            copy.put(e.getKey().toLowerCase(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    // ───────────────────────── 只读 ─────────────────────────

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public List<String> headers(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values == null ? List.of() : values;
    }

    @Override
    public java.util.Set<String> headerNames() {
        return headers.keySet();
    }

    @Override
    public String queryParam(String name) {
        List<String> values = queryParams.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    @Override
    public Map<String, String> pathVariables() {
        return pathVariables;
    }

    // ───────────────────────── attributes ─────────────────────────

    @SuppressWarnings("unchecked")
    @Override
    public <T> T attribute(String name) {
        return (T) attributes.get(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T setAttribute(String name, T value) {
        return (T) attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T removeAttribute(String name) {
        return (T) attributes.remove(name);
    }

    // ───────────────────────── response ─────────────────────────

    @Override
    public int responseStatus() {
        return responseStatus;
    }

    @Override
    public void setResponseStatus(int status) {
        this.responseStatus = status;
    }

    @Override
    public Map<String, String> responseHeaders() {
        return responseHeaders;
    }

    @Override
    public void addResponseHeader(String name, String value) {
        responseHeaders.put(name, value);
    }

    // ───────────────────────── clientKey / traceId ─────────────────────────

    @Override
    public String clientKey() {
        return clientKey;
    }

    @Override
    public void setClientKey(String key) {
        this.clientKey = key;
    }

    @Override
    public String traceId() {
        return traceId;
    }

    @Override
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    // ───────────────────────── short circuit ─────────────────────────

    @Override
    public boolean isShortCircuited() {
        return shortCircuited;
    }

    @Override
    public void markShortCircuit(int status, String body) {
        this.responseStatus = status;
        this.shortCircuitBody = body;
        this.shortCircuited = true;
    }

    @Override
    public String shortCircuitBody() {
        return shortCircuitBody;
    }

    // ───────────────────────── error ─────────────────────────

    @Override
    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public void setError(Throwable error) {
        this.error = error;
    }

    // ───────────────────────── lifecycle ─────────────────────────

    @Override
    public long startNanos() {
        return startNanos;
    }

    @Override
    public void close() {
        if (closed) {
            return; // 幂等
        }
        closed = true;
        // HangDetection 见 §4.4：在 close 时 cancel ScheduledFuture。本基类不持有，由具体 ctx 子类或
        // 拦截器负责；close 的核心是"通知清理 + 防止重复释放"。
        // RequestCompleted 事件的 publish 由 §4.5 HookBus 拦截器在链末调用 ctx.close() 之后发布。
        log.debug("WebRequestContext closed: method={} path={} status={} shortCircuited={} hasError={}",
                method, path, responseStatus, shortCircuited, error != null);
    }

    // ───────────────────────── 便捷构造（builder 风格） ─────────────────────────

    /**
     * 简单的 builder，便于测试与适配层构造。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String method = "GET";
        private String path = "/";
        private final Map<String, List<String>> headers = new HashMap<>();
        private final Map<String, List<String>> queryParams = new HashMap<>();
        private final Map<String, String> pathVariables = new HashMap<>();

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder header(String name, String value) {
            headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder queryParam(String name, String value) {
            queryParams.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder pathVariable(String name, String value) {
            pathVariables.put(name, value);
            return this;
        }

        public MutableWebRequestContext build() {
            return new MutableWebRequestContext(method, path, headers, queryParams, pathVariables);
        }
    }
}