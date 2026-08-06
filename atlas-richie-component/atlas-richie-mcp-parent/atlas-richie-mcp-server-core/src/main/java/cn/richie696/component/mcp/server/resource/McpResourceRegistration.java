package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;

import java.util.Objects;

public record McpResourceRegistration(McpResourceDescriptor descriptor, McpResourceHandler handler) {
    public McpResourceRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
