package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpException;

import java.util.Objects;

/**
 * 业务可恢复错误；Dispatcher 将其转换为 isError=true 的 Tool Result。
 *
 * <p>当业务 Tool 在执行过程中遇到"可向用户/模型解释的失败"时（例如参数缺失、
 * 权限不足、调用方未提供必填输入），应抛出该异常而非普通 {@link McpException}。
 * Dispatcher 捕获后会把 {@link #structuredContent()} 与消息一起包装为
 * {@code isError=true} 的 {@link cn.richie696.component.mcp.api.model.McpToolResponse}
 * 返回给 Client，模型可基于结构化内容进行自动重试或纠错。</p>
 *
 * <p>为什么是"model safe"消息：本异常的 message 字段最终会进入 LLM 的上下文，
 * 因此必须避免泄漏内部堆栈/敏感信息；只暴露"为什么失败 + 如何修正"的提示性消息。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpToolExecutionException extends McpException {
    private final Object structuredContent;

    /**
     * 仅含消息的构造器，无结构化内容与根因。
     *
     * @param modelSafeMessage 面向 LLM/调用方的可读消息
     */
    public McpToolExecutionException(String modelSafeMessage) {
        this(modelSafeMessage, null, null);
    }

    /**
     * 完整构造器。
     *
     * @param modelSafeMessage 面向 LLM/调用方的可读消息
     * @param structuredContent 可选的结构化内容（被 LLM 消费以便自动纠错）
     * @param cause 根因
     */
    public McpToolExecutionException(
            String modelSafeMessage,
            Object structuredContent,
            Throwable cause) {
        super("MCP_TOOL_EXECUTION_ERROR", requireMessage(modelSafeMessage), cause);
        this.structuredContent = structuredContent;
    }

    /**
     * 获取结构化内容。Dispatcher 会把它与可读消息一并放入返回给 Client 的 Tool Result。
     *
     * @return 结构化内容（可为 {@code null}）
     */
    public Object structuredContent() {
        return structuredContent;
    }

    /**
     * 校验消息非空。
     *
     * @param message 待校验消息
     * @return 原样返回
     * @throws NullPointerException 当 {@code message} 为 {@code null} 时
     * @throws IllegalArgumentException 当 {@code message} 为空白字符串时
     */
    private static String requireMessage(String message) {
        if (Objects.requireNonNull(message, "modelSafeMessage").isBlank()) {
            throw new IllegalArgumentException("modelSafeMessage must not be blank");
        }
        return message;
    }
}
