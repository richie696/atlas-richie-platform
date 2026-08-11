package cn.richie696.component.mcp.server.prompt;

import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.server.McpPromptHandler;

import java.util.Objects;

/**
 * MCP Prompt 注册项：把 Prompt 描述符与渲染处理器绑定。
 *
 * <p>作为不可变 record 充当注册表与执行器之间的桥接：描述符提供"输入参数 schema"
 * 以便注册表做参数校验，处理器负责按入参渲染多轮对话模板。
 * record 的自动生成 {@code equals/hashCode} 同样被 {@link McpPromptRegistry} 用于
 * 检测"等价重复注册"。</p>
 *
 * @param descriptor Prompt 描述符（含名称、参数定义等）
 * @param handler    实际渲染 Prompt 内容的处理器
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpPromptRegistration(McpPromptDescriptor descriptor, McpPromptHandler handler) {
    public McpPromptRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
