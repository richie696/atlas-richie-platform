package com.richie.component.http.okhttp;

import com.richie.component.http.core.SseConnection;
import okhttp3.Response;
import okhttp3.sse.EventSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OkHttp 实现 {@link SseConnection} 的 SSE 连接句柄。
 * <p>
 * 封装 {@link EventSource} 和 {@link Response}，提供 HTTP 状态码、响应头、连接状态等信息的访问。
 * 线程安全，{@link #close()} 方法可重复调用且保证幂等。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
class OkHttpSseConnection implements SseConnection {

    private final AtomicReference<Response> responseRef = new AtomicReference<>();
    private final AtomicBoolean opened = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> failureRef = new AtomicReference<>();
    private volatile EventSource eventSource;

    void setEventSource(EventSource eventSource) {
        this.eventSource = eventSource;
    }

    void setResponse(Response response) {
        this.responseRef.set(response);
    }

    void markOpened() {
        this.opened.set(true);
    }

    void markClosed() {
        this.closed.set(true);
    }

    void setFailure(Throwable cause) {
        this.failureRef.set(cause);
    }

    /**
     * 返回 HTTP 状态码。
     * <p>
     * 仅当连接已打开后该值才有意义；若连接尚未建立或已关闭，返回 {@code -1}。
     *
     * @return HTTP 状态码，或 {@code -1}
     */
    @Override
    public int statusCode() {
        Response resp = responseRef.get();
        if (resp == null) {
            return -1;
        }
        return resp.code();
    }

    /**
     * 返回 HTTP 响应头。
     * <p>
     * 模型与 {@code OkHttp Response.headers().toMultimap()} 保持一致。
     *
     * @return 响应头映射
     */
    @Override
    public Map<String, List<String>> headers() {
        Response resp = responseRef.get();
        if (resp == null) {
            return Map.of();
        }
        return resp.headers().toMultimap();
    }

    /**
     * 判断当前连接是否仍处于打开状态。
     *
     * @return {@code true} 表示流仍然存活
     */
    @Override
    public boolean isOpen() {
        return opened.get() && !closed.get() && failureRef.get() == null;
    }

    /**
     * 主动关闭连接，幂等操作。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            EventSource es = this.eventSource;
            if (es != null) {
                es.cancel();
            }
        }
    }

}
