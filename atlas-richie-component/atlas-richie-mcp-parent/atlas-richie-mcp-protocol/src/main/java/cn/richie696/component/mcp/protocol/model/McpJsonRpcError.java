package cn.richie696.component.mcp.protocol.model;

import java.util.Objects;

/**
 * JSON-RPC 2.0 错误对象（{@code error.code} / {@code error.message} / {@code error.data}）。
 *
 * <p>为什么独立建模：JSON-RPC 2.0 规定响应 {@code error} 是三字段对象（{@code code}、
 * {@code message}、{@code data}），但很多业务侧在处理时直接用 {@link McpProtocolException}
 * 表达。本 record 在"线格式"与"异常形态"之间提供显式的中间载体，便于：
 * <ul>
 *   <li>序列化层直接复用 record 的访问器，避免散落的 getter。</li>
 *   <li>校验层在 record 上做强约束（如 {@code message} 非空）。</li>
 *   <li>避免在响应对象 {@link McpJsonRpcResponse} 中嵌套可变对象。</li>
 * </ul>
 * </p>
 *
 * @param code    JSON-RPC 错误码（{@code int}），标准区间 {@code -32768} ~ {@code -32000}
 * @param message 人类可读的错误描述（必填）
 * @param data    可选附加上下文（任意可序列化对象）
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpJsonRpcError(int code, String message, Object data) {
    /**
     * 紧凑构造器：强制 {@code message} 非空，避免下游日志/告警中出现空描述。
     */
    public McpJsonRpcError {
        message = Objects.requireNonNull(message, "message");
    }
}
