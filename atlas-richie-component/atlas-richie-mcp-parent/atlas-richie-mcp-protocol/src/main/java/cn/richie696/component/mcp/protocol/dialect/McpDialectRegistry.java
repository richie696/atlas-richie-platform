package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 以协议版本为键选择 Dialect；升级协议时新增实现并注册即可。
 */
public final class McpDialectRegistry {
    private final Map<String, McpProtocolDialect> dialects;

    public McpDialectRegistry() {
        this(List.of(new Mcp20260728Dialect(), new Mcp20251125Dialect()));
    }

    public McpDialectRegistry(Collection<? extends McpProtocolDialect> dialects) {
        Objects.requireNonNull(dialects, "dialects");
        Map<String, McpProtocolDialect> indexed = new LinkedHashMap<>();
        for (McpProtocolDialect dialect : dialects) {
            McpProtocolDialect duplicate = indexed.put(dialect.version(), dialect);
            if (duplicate != null) {
                throw new IllegalArgumentException("Duplicate MCP dialect: " + dialect.version());
            }
        }
        this.dialects = Map.copyOf(indexed);
    }

    public McpProtocolDialect require(String version) {
        McpProtocolDialect dialect = dialects.get(version);
        if (dialect == null) {
            throw new McpProtocolException(
                    "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                    -32022,
                    "No dialect registered for MCP protocol version: " + version,
                    Map.of("supportedVersions", dialects.keySet()));
        }
        return dialect;
    }

    public Collection<McpProtocolDialect> dialects() {
        return dialects.values();
    }
}
