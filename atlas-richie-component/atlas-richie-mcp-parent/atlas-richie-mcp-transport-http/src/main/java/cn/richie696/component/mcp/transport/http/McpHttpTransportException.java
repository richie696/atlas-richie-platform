package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Optional;

/**
 * 服务端的 HTTP 状态码与可选协议错误的组合，Web 适配器只负责序列化。
 *
 * <p>把 {@link McpProtocolException} 与一个 HTTP 状态码绑在一起，使协议层不必了解 HTTP，
 * HTTP 适配器也不必了解 MCP 协议错误码；{@link McpServerHttpEndpoint} 在处理流程的 catch 分支
 * 把协议错误抛出为本异常，统一交给 Web 适配器按规定结构写到响应体。</p>
 *
 * <p>这是与 {@link McpHttpClientException} 相对应的服务端类型——两者字段几乎一致但语义轴不同：
 * 客户端侧同时携带响应头以便上层处理 401/429，服务端侧不需要响应头但需要 Web 适配器复用它的
 * {@link #httpStatus()} 来决定 HTTP 状态码。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpHttpTransportException extends RuntimeException {
    private final int httpStatus;
    private final McpProtocolException protocolError;

    /**
     * 构造带协议错误的复合异常。{@link RuntimeException#initCause} 自动设为 {@code protocolError}，
     * 因此在普通异常链路展示中也能看到原始协议错误。
     *
     * @param httpStatus    要在 HTTP 响应中使用的状态码
     * @param message       描述信息
     * @param protocolError 关联的协议错误，可为 null（仅是 HTTP 层错误时）
     */
    public McpHttpTransportException(
            int httpStatus,
            String message,
            McpProtocolException protocolError) {
        super(message, protocolError);
        this.httpStatus = httpStatus;
        this.protocolError = protocolError;
    }

    /**
     * @return HTTP 状态码
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * @return 关联的协议错误；不存在时为空 Optional
     */
    public Optional<McpProtocolException> protocolError() {
        return Optional.ofNullable(protocolError);
    }
}
