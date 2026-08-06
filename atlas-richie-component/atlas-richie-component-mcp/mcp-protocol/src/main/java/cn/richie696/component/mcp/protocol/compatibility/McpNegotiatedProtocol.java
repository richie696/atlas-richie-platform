package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;

import java.time.Instant;
import java.util.Objects;

/**
 * A protocol version selected for one remote MCP server.
 */
public record McpNegotiatedProtocol(String version, Instant expiresAt) {
    public McpNegotiatedProtocol {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (!McpProtocolVersions.SUPPORTED.contains(version)) {
            throw new IllegalArgumentException("Unsupported MCP protocol version: " + version);
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }
}
