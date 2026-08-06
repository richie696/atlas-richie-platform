package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.Map;

/**
 * 单个 MCP 协议版本与归一化模型之间的防腐层。
 */
public interface McpProtocolDialect {
    String version();

    McpProtocolEra era();

    McpNormalizedRequest normalizeRequest(McpJsonRpcRequest request, String transportProtocolVersion);

    McpNormalizedResult normalizeResult(Map<String, Object> result);

    Map<String, Object> encodeResult(McpNormalizedResult result);
}
