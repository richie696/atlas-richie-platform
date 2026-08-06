package cn.richie696.component.mcp.server.prompt;

import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.server.McpPromptHandler;

import java.util.Objects;

public record McpPromptRegistration(McpPromptDescriptor descriptor, McpPromptHandler handler) {
    public McpPromptRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
