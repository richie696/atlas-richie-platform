package cn.richie696.component.mcp.api;

/**
 * MCP 组件对业务暴露的异常基类。
 *
 * <p>该异常是 MCP 体系内所有受控失败的统一根类：它同时携带 {@code errorCode}（机器可读的错误码）
 * 与 {@code message}（面向 LLM/开发者的可读消息），并通过 {@link #errorCode()} 暴露为 record 风格
 * 访问器。业务侧可通过 {@code catch (McpException e)} 一并处理所有 MCP 相关错误，
 * 并根据 {@code errorCode} 路由到不同处理策略（重试、降级、上报）。</p>
 *
 * <p>选择 {@link RuntimeException} 作为父类的原因：MCP 体系强调异步与协作式取消，
 * 业务回调多为 {@code CompletionStage}/{@code Consumer}，checked exception 会污染
 * 函数式接口签名，因此统一上抛非受检异常。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public class McpException extends RuntimeException {
    private final String errorCode;

    /**
     * 仅含错误码与消息的构造器。
     *
     * @param errorCode 业务可分类的错误码（如 {@code MCP_CALL_CANCELLED}）
     * @param message 面向开发者的可读消息
     */
    public McpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 携带根因的构造器。
     *
     * @param errorCode 业务可分类的错误码
     * @param message 面向开发者的可读消息
     * @param cause 触发本异常的根因
     */
    public McpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码，业务侧可基于该值做分类（重试/告警/降级）。
     *
     * @return 非空错误码字符串
     */
    public String errorCode() {
        return errorCode;
    }
}
