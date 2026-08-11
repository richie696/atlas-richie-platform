package cn.richie696.component.mcp.testkit;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 协议兼容性测试的标准夹具：为组件消费者提供"开箱即用"的 JSON-RPC 请求样例。
 *
 * <p>本类集中维护两种典型协议版本下的请求形态：(1) 现代版（{@code 2026-07-28}）通过 {@code _meta} 元数据传递
 * 协议版本、客户端信息与能力声明；(2) legacy 版（{@code 2025-11-25}）则把协议版本/客户端信息/能力作为 params
 * 顶层字段传递——这是协议演进过程中的关键差异点。把这些差异固化为夹具可避免每个测试都重复一份硬编码。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpProtocolFixtures {
    private McpProtocolFixtures() {
    }

    /**
     * 构造一个现代版（2026-07-28）的 JSON-RPC 请求，将协议版本/客户端信息/能力通过 {@code _meta} 注入。
     *
     * @param id       JSON-RPC 请求 ID（字符串、数字、{@code null} 均可，遵循 JSON-RPC 2.0 规范）
     * @param method   JSON-RPC 方法名（如 {@code tools/list} / {@code resources/read}）
     * @param arguments 业务参数；为 {@code null} 时按空 Map 处理
     * @return 含 {@code _meta} 注入的 JSON-RPC 请求
     */
    public static McpJsonRpcRequest modernRequest(Object id, String method, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        params.put("_meta", Map.of(
                McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28,
                McpMetaKeys.CLIENT_INFO, Map.of("name", "atlas-test-client", "version", "1.0.0"),
                McpMetaKeys.CLIENT_CAPABILITIES, Map.of()));
        return new McpJsonRpcRequest("2.0", id, method, params);
    }

    /**
     * 构造一个 legacy 协议版本（2025-11-25）下的 {@code initialize} 请求。
     *
     * <p>legacy 协议不通过 {@code _meta} 传递握手信息，而是把 {@code protocolVersion} / {@code clientInfo} / {@code capabilities}
     * 直接作为 params 顶层字段。该夹具专用于验证握手期协议版本协商逻辑与向下兼容路径。</p>
     *
     * @param id JSON-RPC 请求 ID
     * @return 符合 2025-11-25 协议的 initialize 请求
     */
    public static McpJsonRpcRequest legacyInitialize(Object id) {
        return new McpJsonRpcRequest("2.0", id, "initialize", Map.of(
                "protocolVersion", McpProtocolVersions.V_2025_11_25,
                "clientInfo", Map.of("name", "atlas-test-client", "version", "1.0.0"),
                "capabilities", Map.of()));
    }
}
