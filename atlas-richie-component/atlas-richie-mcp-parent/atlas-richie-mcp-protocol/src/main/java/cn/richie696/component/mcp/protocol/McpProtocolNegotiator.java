package cn.richie696.component.mcp.protocol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一处理 Server 与 Client 的版本选择，顺序代表本组件偏好。
 */
public final class McpProtocolNegotiator {
    private final List<String> supportedVersions;

    public McpProtocolNegotiator() {
        this(McpProtocolVersions.SUPPORTED);
    }

    public McpProtocolNegotiator(List<String> supportedVersions) {
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        this.supportedVersions = List.copyOf(new LinkedHashSet<>(supportedVersions));
        if (this.supportedVersions.isEmpty()) {
            throw new IllegalArgumentException("At least one protocol version is required");
        }
    }

    public String negotiate(List<String> peerVersions) {
        Set<String> offered = new LinkedHashSet<>(Objects.requireNonNull(peerVersions, "peerVersions"));
        return supportedVersions.stream()
                .filter(offered::contains)
                .findFirst()
                .orElseThrow(() -> new McpProtocolException(
                        "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                        -32022,
                        "No mutually supported MCP protocol version",
                        Map.of("supported", supportedVersions)));
    }

    public void requireSupported(String version) {
        if (!supportedVersions.contains(version)) {
            throw new McpProtocolException(
                    "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                    -32022,
                    "Unsupported MCP protocol version: " + version,
                    Map.of("supported", supportedVersions, "requested", version));
        }
    }

    public List<String> supportedVersions() {
        return supportedVersions;
    }
}
