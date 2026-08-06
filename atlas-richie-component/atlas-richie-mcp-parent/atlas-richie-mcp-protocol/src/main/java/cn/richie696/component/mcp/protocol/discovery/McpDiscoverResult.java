package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * server/discover 的协议无关内部结果。
 */
public record McpDiscoverResult(
        List<String> supportedVersions,
        Map<String, Object> capabilities,
        McpImplementationInfo serverInfo,
        String instructions,
        long ttlMs,
        McpCacheScope cacheScope,
        Map<String, Object> extensions) {

    public McpDiscoverResult {
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        supportedVersions = List.copyOf(new LinkedHashSet<>(supportedVersions));
        if (supportedVersions.isEmpty() || supportedVersions.stream().anyMatch(version -> version == null
                || version.isBlank())) {
            throw new IllegalArgumentException("supportedVersions must contain at least one non-blank version");
        }
        capabilities = capabilities == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
        if (ttlMs < 0) {
            throw new IllegalArgumentException("ttlMs must be a non-negative integer");
        }
        cacheScope = Objects.requireNonNull(cacheScope, "cacheScope");
        extensions = extensions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }
}
