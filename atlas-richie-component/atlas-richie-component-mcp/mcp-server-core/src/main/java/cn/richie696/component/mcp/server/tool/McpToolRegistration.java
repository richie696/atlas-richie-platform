package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.server.McpToolHandler;

import java.util.Objects;

public record McpToolRegistration(McpToolDescriptor descriptor, McpToolHandler handler) {
    public McpToolRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
