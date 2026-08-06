package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

/**
 * 已完成 HTTP/body 交叉验证的 modern MCP 请求。
 */
public record McpValidatedHttpRequest(
        String protocolVersion,
        McpJsonRpcRequest message) {
}
