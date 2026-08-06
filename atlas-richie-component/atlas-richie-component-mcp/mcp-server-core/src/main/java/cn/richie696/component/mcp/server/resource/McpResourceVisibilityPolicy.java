package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;

@FunctionalInterface
public interface McpResourceVisibilityPolicy {
    McpResourceVisibilityPolicy ALLOW_ALL = (descriptor, context) -> true;

    boolean isVisible(McpResourceDescriptor descriptor, McpCallContext context);
}
