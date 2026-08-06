package cn.richie696.component.mcp.protocol.discovery;

import java.util.LinkedHashMap;
import java.util.Map;

/** Common cache metadata for complete MCP results. */
public final class McpCacheHints {
    public static final String TTL_MS = "ttlMs";
    public static final String CACHE_SCOPE = "cacheScope";

    private McpCacheHints() {
    }

    public static Map<String, Object> add(Map<String, Object> result, long ttlMs, McpCacheScope scope) {
        if (ttlMs < 0) {
            throw new IllegalArgumentException("ttlMs must be non-negative");
        }
        Map<String, Object> copy = new LinkedHashMap<>(result == null ? Map.of() : result);
        copy.put(TTL_MS, ttlMs);
        copy.put(CACHE_SCOPE, scope.wireValue());
        return copy;
    }
}
