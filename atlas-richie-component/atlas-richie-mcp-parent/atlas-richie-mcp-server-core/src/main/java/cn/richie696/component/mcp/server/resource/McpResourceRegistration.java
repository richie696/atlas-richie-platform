package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;

import java.util.Objects;

/**
 * MCP Resource 注册项：把精确 URI 资源描述符与读取处理器绑定。
 *
 * <p>作为不可变 record 承担双重职责：保证描述符与处理器始终成对，
 * 同时通过 {@link McpResourceRegistry#resolve(String, cn.richie696.component.mcp.api.McpCallContext)}
 * 时作为返回值由调用方直接复用，避免二次构造。</p>
 *
 * @param descriptor 资源描述符（URI、名称、MIME、annotations 等）
 * @param handler    读取资源内容的处理器
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpResourceRegistration(McpResourceDescriptor descriptor, McpResourceHandler handler) {
    public McpResourceRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
