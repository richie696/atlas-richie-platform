package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;

import java.util.List;

public record McpToolRegistrySnapshot(long revision, List<McpToolDescriptor> tools) {
    public McpToolRegistrySnapshot {
        tools = List.copyOf(tools);
    }
}
