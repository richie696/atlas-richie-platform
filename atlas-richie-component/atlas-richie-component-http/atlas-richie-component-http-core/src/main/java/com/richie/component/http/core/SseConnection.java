package com.richie.component.http.core;

import java.util.List;
import java.util.Map;

/**
 * SSE 长连接的句柄。
 * <p>
 * 用于在 {@link SseListener} 回调中获取响应元信息，并在业务侧需要时主动关闭流。
 * 实现类必须保证 {@link #close()} 的幂等性：重复关闭不应抛出异常。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface SseConnection extends AutoCloseable {

    /**
     * HTTP 状态码。
     * <p>
     * 仅当 {@link SseListener#onOpen(SseConnection)} 触发后该值才有意义；
     * 若连接尚未建立或已异常关闭，应返回 {@code -1}。
     *
     * @return HTTP 状态码
     */
    int statusCode();

    /**
     * HTTP 响应头（name → values）。
     * <p>
     * 模型与 {@link HttpResponse#headers()} 保持一致。
     *
     * @return 响应头映射
     */
    Map<String, List<String>> headers();

    /**
     * 当前连接是否仍处于打开状态。
     *
     * @return {@code true} 表示流仍然存活
     */
    boolean isOpen();

    /**
     * 主动关闭连接，幂等。
     */
    @Override
    void close();

}