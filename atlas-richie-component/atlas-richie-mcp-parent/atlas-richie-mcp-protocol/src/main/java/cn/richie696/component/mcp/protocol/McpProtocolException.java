package cn.richie696.component.mcp.protocol;

import cn.richie696.component.mcp.api.McpException;

import java.util.Map;

/**
 * 可无损映射为 JSON-RPC error 的协议异常。
 *
 * <p>该异常是 {@link McpException} 的特化，专门承载"可被序列化回 JSON-RPC {@code error}"
 * 形态的信息。设计目标是：协议层任何位置抛出的错误，都能被外层传输模块直接转换为
 * 标准的 JSON-RPC 错误响应，而不需要业务侧再做"异常 → 错误对象"的二次映射。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>同时持有 {@code errorCode}（业务级语义码，给监控/告警用）和
 *       {@code jsonRpcCode}（JSON-RPC 错误码，如 {@code -32600/-32602/-32020/-32022}），
 *       两者解耦让业务码和协议码各自独立演进。</li>
 *   <li>{@code data} 字段是 {@link Map#copyOf} 后的不可变副本，避免外部对异常的
 *       二次修改影响错误响应的可重现性。</li>
 *   <li>{@code null} data 在构造时归一为 {@link Map#of()}，简化下游判空逻辑。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpProtocolException extends McpException {
    private final int jsonRpcCode;
    private final Map<String, Object> data;

    /**
     * 构造一个协议层异常。
     *
     * @param errorCode    业务级错误码（如 {@code "MCP_INVALID_PARAMS"}），用于监控与告警
     * @param jsonRpcCode  JSON-RPC 错误码（标准区间：{@code -32768} ~ {@code -32000}）
     * @param message      人类可读的错误描述
     * @param data         附加上下文；为 {@code null} 时归一为 {@link Map#of()}
     */
    public McpProtocolException(String errorCode, int jsonRpcCode, String message, Map<String, Object> data) {
        super(errorCode, message);
        this.jsonRpcCode = jsonRpcCode;
        // 防御性拷贝 + 不可变视图：避免外部修改 data 影响错误响应的可重现性
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    /**
     * 构造一个带根因的协议层异常。
     *
     * @param errorCode    业务级错误码
     * @param jsonRpcCode  JSON-RPC 错误码
     * @param message      人类可读的错误描述
     * @param data         附加上下文；为 {@code null} 时归一为 {@link Map#of()}
     * @param cause        原始异常（用于堆栈追溯）
     */
    public McpProtocolException(
            String errorCode,
            int jsonRpcCode,
            String message,
            Map<String, Object> data,
            Throwable cause) {
        super(errorCode, message, cause);
        this.jsonRpcCode = jsonRpcCode;
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    /**
     * 返回 JSON-RPC 错误码（{@code code} 字段）。
     *
     * @return JSON-RPC 错误码整数值
     */
    public int jsonRpcCode() {
        return jsonRpcCode;
    }

    /**
     * 返回不可变的附加上下文（对应 JSON-RPC {@code data} 字段）。
     *
     * @return 上下文 Map，永远非 {@code null}
     */
    public Map<String, Object> data() {
        return data;
    }
}
