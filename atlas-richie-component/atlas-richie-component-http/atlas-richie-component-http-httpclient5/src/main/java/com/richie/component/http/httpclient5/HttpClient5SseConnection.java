package com.richie.component.http.httpclient5;

import com.richie.component.http.core.SseConnection;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Apache HttpClient5 的 SSE 连接实现。
 * <p>
 * 持有 SSE 流的 HTTP 响应元信息（状态码、响应头）以及底层连接，
 * 并通过 {@link #close()} 主动关闭流。实现 {@link AutoCloseable} 语义，
 * 支持 try-with-resources 写法。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class HttpClient5SseConnection implements SseConnection {

    private volatile int statusCode = -1;
    private volatile Map<String, List<String>> headers = Collections.emptyMap();
    private volatile boolean open = true;

    private CloseableHttpResponse response;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 设置 HTTP 响应并提取状态码和响应头。
     *
     * @param response Apache HC5 的经典 HTTP 响应
     */
    void setResponse(CloseableHttpResponse response) {
        this.response = response;
        this.statusCode = response.getCode();
        var headerMap = new java.util.HashMap<String, List<String>>();
        for (var h : response.getHeaders()) {
            headerMap.computeIfAbsent(h.getName(), k -> new java.util.ArrayList<>()).add(h.getValue());
        }
        this.headers = Collections.unmodifiableMap(headerMap);
    }

    /**
     * 标记连接已被服务端关闭。
     */
    void markClosed() {
        this.open = false;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public Map<String, List<String>> headers() {
        return headers;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * 关闭 SSE 连接，释放底层 HTTP 资源。
     * <p>
     * 本方法幂等：重复调用不会抛出异常。
     * 关闭逻辑：
     * <ol>
     *   <li>原子地设置关闭标志，防止重复关闭。</li>
     *   <li>将 {@code open} 标记为 {@code false}。</li>
     *   <li>若调用线程正是读线程，则中断之。</li>
     *   <li>关闭底层 {@link CloseableHttpResponse}，释放连接。</li>
     * </ol>
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        this.open = false;
        if (response != null) {
            try {
                response.close();
            } catch (Exception ignored) {
                // 关闭连接时忽略异常
            }
        }
    }

}
