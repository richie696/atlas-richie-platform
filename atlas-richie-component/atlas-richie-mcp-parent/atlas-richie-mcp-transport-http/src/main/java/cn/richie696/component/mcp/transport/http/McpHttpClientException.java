package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Optional;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧稳定的失败类型，使 Wire/SDK 异常不越过适配器边界。
 *
 * <p>{@link McpHttpToolClient} 在以下场景抛出本异常：
 * HTTP 状态非 2xx、响应体不是合法 JSON-RPC envelope、协议错误（例如 {@code -32601 method not found}）
 * 或本地反序列化失败。这些失败在客户端应用中往往需要按 HTTP 状态码或 JSON-RPC {@code code}
 * 字段做策略化处理（例如 401 的 Refresh Token、429 的退避），因此本异常
 * 同时携带 {@link #httpStatus()} 与 {@link #protocolError()} 而不是单继承 {@link McpProtocolException}，
 * 让上层可以一次性看到"HTTP 层 + 协议层"的复合信号。</p>
 *
 * <p>为何保证"wire 异常不会外泄"：调用方编写的客户端代码通常依赖 JDK 异常或应用级异常做
 * fallback 决策；如果混入 OkHttp / JDK HttpClient / Jackson 的内部异常会迫使调用方了解底层 SDK，
 * 反而失去适配器抽象的意义。本类禁止携带这三个层级的原始 cause 透出（仅作为构造参数接收但不暴露）。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpHttpClientException extends RuntimeException {
    private final int httpStatus;
    private final McpProtocolException protocolError;
    private final Map<String, List<String>> responseHeaders;

    /**
     * 不关心响应头时的便捷构造（向 {@link #responseHeaders()} 提供空 Map）。
     *
     * @param message       客户端可见的描述信息
     * @param httpStatus    HTTP 状态码（远程错误时为服务端返回码，本地失败时为 0）
     * @param protocolError 对应的 JSON-RPC / MCP 协议错误，可为 null
     * @param cause         触发本异常的原始 cause；不再被本类透出，但不丢失以便调试
     */
    public McpHttpClientException(String message, int httpStatus, McpProtocolException protocolError, Throwable cause) {
        this(message, httpStatus, protocolError, cause, Map.of());
    }

    /**
     * 携带响应头的完整构造。{@link #responseHeaders()} 主要服务于 OAuth 等需要根据 401 的
     * {@code WWW-Authenticate} 头做后续动作的场景。
     *
     * @param message         描述信息
     * @param httpStatus      HTTP 状态码
     * @param protocolError   对应协议错误，可为 null
     * @param cause           原始 cause（被父类保留，但不通过本类方法透出）
     * @param responseHeaders 远端响应头；{@code null} 替换为空 Map
     */
    public McpHttpClientException(
            String message,
            int httpStatus,
            McpProtocolException protocolError,
            Throwable cause,
            Map<String, List<String>> responseHeaders) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.protocolError = protocolError;
        this.responseHeaders = responseHeaders == null ? Map.of() : Map.copyOf(responseHeaders);
    }

    /**
     * @return HTTP 状态码；本地层失败时为 0
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * @return 协议层错误，不存在时为空 Optional
     */
    public Optional<McpProtocolException> protocolError() {
        return Optional.ofNullable(protocolError);
    }

    /**
     * 取得远端响应的全部 HTTP 头，多值 Map 形式（含 401 挑战时的 {@code WWW-Authenticate}）。
     *
     * @return 不可变响应头映射；构造时为 null 时返回空 Map
     */
    public Map<String, List<String>> responseHeaders() {
        return responseHeaders;
    }

    /**
     * 在响应头中按大小写不敏感方式查找某个头名称的第一个值，常用于读取 {@code WWW-Authenticate}
     * / {@code Retry-After} 等单值头。
     *
     * @param name HTTP 头名称
     * @return 找到的首个值，不存在时为空 Optional
     */
    public Optional<String> firstHeader(String name) {
        return responseHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
