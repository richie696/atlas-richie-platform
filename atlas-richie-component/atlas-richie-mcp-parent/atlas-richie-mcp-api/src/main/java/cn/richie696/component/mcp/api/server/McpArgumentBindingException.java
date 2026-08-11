package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpException;

/**
 * Safe business-facing error raised when one Tool argument cannot be bound.
 *
 * <p>该异常是 {@link McpException} 的特化子类，专用于"参数绑定阶段"失败：例如协议层
 * 给定的是字符串，但目标字段是 {@code Integer} 且内容非法；或者 enum 值不在
 * {@link McpArgumentMetadata#enumValues()} 范围内。</p>
 *
 * <p>把"绑定失败"独立为一类异常的好处：</p>
 * <ol>
 *   <li>框架可以单独捕获并向 Client 返回明确的"参数名+原因"，便于 LLM 自动纠错；</li>
 *   <li>与"业务执行失败"（{@link McpToolExecutionException}）隔离，避免一类问题掩盖另一类。</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpArgumentBindingException extends McpException {
    private final String argumentName;

    /**
     * 仅含消息的构造器。
     *
     * @param argumentName 触发失败的业务参数名
     * @param message 失败原因描述
     */
    public McpArgumentBindingException(String argumentName, String message) {
        super("MCP_TOOL_ARGUMENT_BINDING_FAILED", message);
        this.argumentName = argumentName;
    }

    /**
     * 携带根因的构造器。
     *
     * @param argumentName 触发失败的业务参数名
     * @param message 失败原因描述
     * @param cause 底层转换异常
     */
    public McpArgumentBindingException(String argumentName, String message, Throwable cause) {
        super("MCP_TOOL_ARGUMENT_BINDING_FAILED", message, cause);
        this.argumentName = argumentName;
    }

    /**
     * 暴露触发失败的业务参数名，便于协议层把它作为结构化字段返回给 Client。
     *
     * @return 参数名
     */
    public String argumentName() {
        return argumentName;
    }
}
