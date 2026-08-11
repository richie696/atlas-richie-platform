package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.server.McpToolHandler;

import java.util.Objects;

/**
 * MCP Tool 注册项：将工具描述符与执行处理器绑定，供注册表使用。
 *
 * <p>作为不可变 record 类型，保证在注册到 {@link McpToolRegistry} 后，
 * 描述符与处理器始终成对出现，避免出现"有描述无实现"或"有实现无描述"的残缺状态。
 * 在通过 {@link McpToolRegistry#replace(McpToolRegistration)} 等方法比较两次注册是否等价时，
 * 该 record 的自动生成 {@code equals/hashCode} 也承担身份比较职责。</p>
 *
 * @param descriptor 工具对外发布的描述（名称、inputSchema、outputSchema、annotations 等）
 * @param handler    实际执行业务逻辑的处理器
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolRegistration(McpToolDescriptor descriptor, McpToolHandler handler) {
    public McpToolRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
