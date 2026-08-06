package cn.richie696.component.mcp.client.spring.boot;

/** Explicit cache invalidation hook for list_changed/resource_updated consumers. */
public interface McpClientCacheControl {
    void invalidateServerCache(String serverId);

    void clearCaches();
}
