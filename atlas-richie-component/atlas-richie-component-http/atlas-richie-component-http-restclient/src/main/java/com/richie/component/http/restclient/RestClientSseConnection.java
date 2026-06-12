package com.richie.component.http.restclient;

import com.richie.component.http.core.SseConnection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RestClient SSE 连接实现。
 * <p>
 * 封装 SSE 长连接的状态，包括 HTTP 状态码、响应头、连接是否打开等。
 * 提供幂等的 close() 方法，支持中断底层读取线程。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
class RestClientSseConnection implements SseConnection {

    private volatile int statusCode = -1;
    private volatile Map<String, List<String>> headers = Map.of();
    private volatile boolean open = true;
    private volatile Thread workerThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 设置 HTTP 状态码。
     *
     * @param statusCode HTTP 状态码
     */
    void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * 设置 HTTP 响应头。
     *
     * @param headers 响应头映射
     */
    void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers != null ? headers : Map.of();
    }

    /**
     * 标记连接已关闭。
     */
    void markClosed() {
        this.open = false;
    }

    /**
     * 设置工作线程引用。
     *
     * @param workerThread SSE 读取工作线程
     */
    void workerThread(Thread workerThread) {
        this.workerThread = workerThread;
    }

    /**
     * 获取工作线程引用。
     *
     * @return 工作线程，若未设置则返回 null
     */
    Thread getWorkerThread() {
        return workerThread;
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

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            this.open = false;
            Thread thread = this.workerThread;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

}
