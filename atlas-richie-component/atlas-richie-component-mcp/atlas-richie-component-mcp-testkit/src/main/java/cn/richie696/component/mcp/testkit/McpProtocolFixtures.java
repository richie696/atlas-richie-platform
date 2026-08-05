package cn.richie696.component.mcp.testkit;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供组件消费者编写协议兼容性测试的标准夹具。
 */
public final class McpProtocolFixtures {
    private McpProtocolFixtures() {
    }

    public static McpJsonRpcRequest modernRequest(Object id, String method, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        params.put("_meta", Map.of(
                McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28,
                McpMetaKeys.CLIENT_INFO, Map.of("name", "atlas-test-client", "version", "1.0.0"),
                McpMetaKeys.CLIENT_CAPABILITIES, Map.of()));
        return new McpJsonRpcRequest("2.0", id, method, params);
    }

    public static McpJsonRpcRequest legacyInitialize(Object id) {
        return new McpJsonRpcRequest("2.0", id, "initialize", Map.of(
                "protocolVersion", McpProtocolVersions.V_2025_11_25,
                "clientInfo", Map.of("name", "atlas-test-client", "version", "1.0.0"),
                "capabilities", Map.of()));
    }
}
