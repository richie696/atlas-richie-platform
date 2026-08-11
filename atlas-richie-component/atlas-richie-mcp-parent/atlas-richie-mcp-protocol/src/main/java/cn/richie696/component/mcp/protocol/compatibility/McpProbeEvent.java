package cn.richie696.component.mcp.protocol.compatibility;

import java.util.List;

/**
 * 传输层将探测结果归一化为该事件，避免把 HTTP/进程实现泄漏给协商核心。
 *
 * <p>设计意图：探测可能来自不同传输（Streamable HTTP、STDIO 等），每种传输的"失败/成功"
 * 信号差异很大。{@link McpProbeEvent} 抽取共性维度（HTTP 状态码、JSON-RPC 错误码、
 * 声明的协议版本、事件类型），让 {@link McpEraProbeStateMachine} 在不感知具体传输
 * 实现的情况下做出判定。</p>
 *
 * @param type                事件类型
 * @param httpStatus          HTTP 状态码（仅 HTTP 传输相关），可能为 {@code null}
 * @param jsonRpcErrorCode    JSON-RPC 错误码，可能为 {@code null}
 * @param advertisedVersions  对端声明支持的协议版本列表，不可变
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpProbeEvent(
        Type type,
        Integer httpStatus,
        Integer jsonRpcErrorCode,
        List<String> advertisedVersions) {

    /**
     * 紧凑构造器：{@code null} 列表归一为不可变空列表。
     */
    public McpProbeEvent {
        advertisedVersions = advertisedVersions == null ? List.of() : List.copyOf(advertisedVersions);
    }

    /**
     * 构造 {@code DISCOVER_RESULT} 事件。
     *
     * @param supportedVersions 对端声明支持的协议版本列表
     * @return 事件实例
     */
    public static McpProbeEvent discoverResult(List<String> supportedVersions) {
        return new McpProbeEvent(Type.DISCOVER_RESULT, null, null, supportedVersions);
    }

    /**
     * 构造 {@code MODERN_SUCCESS} 事件。
     *
     * @param httpStatus HTTP 状态码
     * @return 事件实例
     */
    public static McpProbeEvent modernSuccess(int httpStatus) {
        return new McpProbeEvent(Type.MODERN_SUCCESS, httpStatus, null, List.of());
    }

    /**
     * 构造 {@code JSON_RPC_ERROR} 事件。
     *
     * @param httpStatus        HTTP 状态码，可为 {@code null}
     * @param jsonRpcErrorCode  JSON-RPC 错误码
     * @param advertisedVersions 对端在错误响应中声明支持的版本（可能为空）
     * @return 事件实例
     */
    public static McpProbeEvent jsonRpcError(
            Integer httpStatus,
            int jsonRpcErrorCode,
            List<String> advertisedVersions) {
        return new McpProbeEvent(
                Type.JSON_RPC_ERROR,
                httpStatus,
                jsonRpcErrorCode,
                advertisedVersions);
    }

    /**
     * 构造 {@code TRANSPORT_ERROR} 事件（如连接失败、协议解析失败）。
     *
     * @param httpStatus HTTP 状态码（可能为 {@code null}）
     * @return 事件实例
     */
    public static McpProbeEvent transportError(Integer httpStatus) {
        return new McpProbeEvent(Type.TRANSPORT_ERROR, httpStatus, null, List.of());
    }

    /**
     * 构造 {@code TIMEOUT} 事件。
     *
     * @return 事件实例
     */
    public static McpProbeEvent timeout() {
        return new McpProbeEvent(Type.TIMEOUT, null, null, List.of());
    }

    /**
     * 事件类型枚举。
     */
    public enum Type {
        /** {@code server/discover} 探测返回的版本声明。 */
        DISCOVER_RESULT,
        /** 现代路径探测成功。 */
        MODERN_SUCCESS,
        /** 收到 JSON-RPC 错误响应。 */
        JSON_RPC_ERROR,
        /** 传输层失败（非 JSON-RPC 层错误）。 */
        TRANSPORT_ERROR,
        /** 探测超时。 */
        TIMEOUT
    }
}
