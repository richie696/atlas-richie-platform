package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;

/**
 * 探测状态机的确定性输出。
 */
public record McpProbeDecision(McpProtocolEra era, Action action, String selectedVersion) {
    public enum Action {
        USE_MODERN,
        RETRY_MODERN,
        INITIALIZE_LEGACY,
        RETRY_PROBE,
        FAIL_INCOMPATIBLE
    }
}
