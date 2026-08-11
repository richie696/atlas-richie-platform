package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

/**
 * 已经完成 HTTP 头与 body 交叉验证以及协议级 JSON-RPC 校验的现代 MCP 请求。
 *
 * <p>它是 {@link McpServerHttpEndpoint} 与 {@link McpStreamableHttpRequestValidator} 之间的桥梁 record：
 * 校验器把通过 {@code Content-Type} / {@code Accept} / {@code MCP-Protocol-Version} 等检查的请求打包成
 * 本类型，端点直接消费不需要再做防御性判断。这把校验路径与业务路径做了解耦：测试中可以跳过验证器
 * 直接构造 {@code McpValidatedHttpRequest} 以隔离业务逻辑。</p>
 *
 * @param protocolVersion 已经校验一致的 2026-07-28 协议版本
 * @param message         JSON-RPC 请求体（已通过 MCP 协议层的 schema 校验）
 * @author richie696
 * @since 2026-08-11
 */
public record McpValidatedHttpRequest(
        String protocolVersion,
        McpJsonRpcRequest message) {
}
