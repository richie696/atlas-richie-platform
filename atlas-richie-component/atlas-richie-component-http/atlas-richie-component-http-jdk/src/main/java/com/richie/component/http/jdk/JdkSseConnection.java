/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.http.jdk;

import com.richie.component.http.core.SseConnection;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JDK HttpClient 实现的 SSE 连接句柄。
 * <p>
 * 持有 SSE 连接的元信息（状态码、响应头、打开状态）以及底层资源（HttpResponse、读取线程）。
 * close() 幂等：重复关闭不会抛异常。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
class JdkSseConnection implements SseConnection {

    private volatile int statusCode = -1;
    private volatile Map<String, List<String>> headers = Collections.emptyMap();
    private volatile boolean open = true;
    private volatile Future<?> workerFuture;
    private volatile java.net.http.HttpResponse<java.io.InputStream> response;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 设置 HTTP 响应，填充状态码和响应头。
     *
     * @param response HTTP 响应
     */
    void setResponse(java.net.http.HttpResponse<java.io.InputStream> response) {
        this.response = response;
        this.statusCode = response.statusCode();
        this.headers = response.headers().map();
    }

    /**
     * 标记连接已关闭（内部使用，通常由读取线程在流结束后调用）。
     */
    void markClosed() {
        this.open = false;
    }

    /**
     * 设置读取 SSE 流的 worker Future，用于 close() 时取消线程。
     *
     * @param workerFuture worker 线程的 Future
     */
    void setWorkerFuture(Future<?> workerFuture) {
        this.workerFuture = workerFuture;
    }

    /**
     * 返回 HTTP 状态码。
     *
     * @return HTTP 状态码，若尚未获取则返回 -1
     */
    @Override
    public int statusCode() {
        return statusCode;
    }

    /**
     * 返回 HTTP 响应头映射。
     *
     * @return 响应头 Map（name → values）
     */
    @Override
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * 返回连接是否仍处于打开状态。
     *
     * @return {@code true} 表示流仍然存活
     */
    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * 关闭连接，释放底层资源。
     * <p>
     * 关闭操作幂等：多次调用不会抛异常。
     * 实现逻辑：
     * <ol>
     *   <li>将 open 标记为 false</li>
     *   <li>调用 response.close() 中断底层读取</li>
     *   <li>取消 worker Future（如果存在）</li>
     * </ol>
     */
    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }
        this.open = false;

        // 关闭 response body（InputStream），会中断正在进行的读取
        java.net.http.HttpResponse<java.io.InputStream> resp = this.response;
        if (resp != null) {
            java.io.InputStream body = resp.body();
            if (body != null) {
                try {
                    body.close();
                } catch (java.io.IOException ignored) {
                }
            }
        }

        // 取消 worker 线程
        Future<?> future = this.workerFuture;
        if (future != null) {
            future.cancel(true);
        }
    }

}
