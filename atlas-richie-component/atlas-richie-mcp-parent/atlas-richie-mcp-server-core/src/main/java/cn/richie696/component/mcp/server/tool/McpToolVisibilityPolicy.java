package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;

/**
 * 根据每次请求的授权上下文决定 Tool 是否可见、可调用。
 */
@FunctionalInterface
public interface McpToolVisibilityPolicy {
    McpToolVisibilityPolicy ALLOW_ALL = (descriptor, context) -> true;

    boolean isVisible(McpToolDescriptor descriptor, McpCallContext context);
}
