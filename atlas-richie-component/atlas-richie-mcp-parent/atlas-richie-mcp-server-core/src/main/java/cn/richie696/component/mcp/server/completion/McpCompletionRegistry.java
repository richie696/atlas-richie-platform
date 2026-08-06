package cn.richie696.component.mcp.server.completion;

import cn.richie696.component.mcp.api.server.McpCompletionHandler;

import java.util.Objects;

/** Single completion provider per server endpoint. */
public final class McpCompletionRegistry {
    private final McpCompletionHandler handler;

    public McpCompletionRegistry(McpCompletionHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public McpCompletionHandler handler() {
        return handler;
    }
}
