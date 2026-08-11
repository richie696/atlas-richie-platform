package cn.richie696.component.mcp.schema;

import java.util.List;

/**
 * JSON Schema 非法时抛出的受检异常，继承自 {@link IllegalArgumentException} 以兼容现有调用栈。
 *
 * <p>在 MCP 体系中，本异常统一承载"schema 自身定义不合法"的所有场景：空 schema、深度超限、节点数超限、外部引用、循环引用、
 * 元模型校验失败等。它把违规列表（{@link McpSchemaViolation}）以不可变形式挂在异常对象上，
 * 便于上游在统一异常处理器中渲染或上报，避免业务代码到处传递 {@code List<McpSchemaViolation>}。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpSchemaDefinitionException extends IllegalArgumentException {
    private final List<McpSchemaViolation> violations;

    /**
     * 构造一个仅含消息、无违规列表与原因的异常。
     *
     * @param message 异常说明，建议包含 schema 名称与具体校验阶段
     */
    public McpSchemaDefinitionException(String message) {
        this(message, List.of(), null);
    }

    /**
     * 构造一个带违规列表与根因的异常。
     *
     * @param message   异常说明
     * @param violations 违规列表（可为 {@code null}，内部规范化为空列表）
     * @param cause     触发本异常的根因（可为 {@code null}）
     */
    public McpSchemaDefinitionException(
            String message,
            List<McpSchemaViolation> violations,
            Throwable cause) {
        super(message, cause);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    /**
     * 返回不可变的违规列表，调用方无需再行拷贝。
     *
     * @return 违规列表；从未产生过具体违规时返回空列表
     */
    public List<McpSchemaViolation> violations() {
        return violations;
    }
}
