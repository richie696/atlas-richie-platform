package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.schema.McpCompiledSchema;

import java.util.Objects;
import java.util.Optional;

/**
 * 注册表在内存中持有的"已解析 Tool"形态：将原始注册项与预编译的 JSON Schema 校验器绑定。
 *
 * <p>在 {@link McpToolRegistry} 注册阶段，input/output Schema 会被预编译为
 * {@link McpCompiledSchema}，避免每次调用都重复解析 JSON Schema 文本，
 * 显著降低请求热路径的 CPU 开销。outputSchema 可选（很多工具不声明输出结构），
 * 因此通过 {@link #optionalOutputSchema()} 暴露 {@link Optional} 形式的访问器，
 * 强制调用方处理"无输出 Schema"的分支，避免空指针。</p>
 *
 * @param registration 原始的 Tool 注册项，包含描述符与执行处理器
 * @param inputSchema  预编译后的入参 JSON Schema 校验器
 * @param outputSchema 预编译后的出参 JSON Schema 校验器；为 null 时表示 Tool 未声明输出结构
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpResolvedTool(
        McpToolRegistration registration,
        McpCompiledSchema inputSchema,
        McpCompiledSchema outputSchema) {

    public McpResolvedTool {
        registration = Objects.requireNonNull(registration, "registration");
        inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
    }

    /**
     * 返回预编译的出参 Schema，若 Tool 未声明输出结构则返回空 Optional。
     *
     * @return 预编译出参 Schema 的 Optional 包装，永不为 null
     */
    public Optional<McpCompiledSchema> optionalOutputSchema() {
        return Optional.ofNullable(outputSchema);
    }
}
