package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;

import java.util.Objects;

public record McpResourceTemplateRegistration(
        McpResourceTemplateDescriptor descriptor,
        McpResourceHandler handler) {
    public McpResourceTemplateRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
