package cn.richie696.component.mcp.transport.http;

import java.util.Map;

/**
 * 远端 MCP 服务返回的工具元数据 record。
 *
 * <p>设计为一个不可变 record 的原因：
 * 工具元数据在客户端通常仅用于"展示/发现"，被一次性消费后即可丢弃；record 自动获得 equals/hashCode/toString
 * 缓存键直接可作为缓存 key；通过构造时的 {@link Map#copyOf} 防御性拷贝，调用方之后修改原始 Map 不会
 * 污染本对象。这同时也避免了 "DCL + 字段可写" 在多线程工具发现上下文下泄漏内部状态。</p>
 *
 * @param name         工具在协议中唯一标识，对应 JSON-RPC {@code tools/call} 的 {@code params.name}
 * @param title        人类可读标题（可空）
 * @param description  工具功能描述
 * @param inputSchema  工具入参 JSON Schema，已深拷贝为不可变 Map
 * @param outputSchema 工具出参 JSON Schema，可为空
 * @param annotations  工具附加 annotations（如 {@code readOnlyHint}、{@code destructiveHint}），可为空
 * @author richie696
 * @since 2026-08-11
 */
public record McpRemoteTool(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations) {
    /**
     * 三个 schema 字段在构造时统一规整为不可变 Map，null 替换为空 Map，方便调用方无脑 {@code .get(...)}。
     */
    public McpRemoteTool {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
