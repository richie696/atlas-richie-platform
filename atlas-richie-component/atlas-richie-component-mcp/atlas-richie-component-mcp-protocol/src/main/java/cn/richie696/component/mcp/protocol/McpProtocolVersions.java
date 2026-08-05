package cn.richie696.component.mcp.protocol;

import java.util.List;

/**
 * 组件已验证的 MCP 协议版本。
 */
public final class McpProtocolVersions {
    public static final String V_2026_07_28 = "2026-07-28";
    public static final String V_2025_11_25 = "2025-11-25";
    public static final List<String> SUPPORTED = List.of(V_2026_07_28, V_2025_11_25);

    private McpProtocolVersions() {
    }
}
