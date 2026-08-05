package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpJsonRpcValidator;
import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2025-11-25 会话/initialize 协议时代适配器。
 */
public final class Mcp20251125Dialect implements McpProtocolDialect {
    @Override
    public String version() {
        return McpProtocolVersions.V_2025_11_25;
    }

    @Override
    public McpProtocolEra era() {
        return McpProtocolEra.SESSION_2025;
    }

    @Override
    public McpNormalizedRequest normalizeRequest(McpJsonRpcRequest request, String transportProtocolVersion) {
        McpJsonRpcValidator.validate(request);
        Map<String, Object> params = request.params();
        boolean initialize = "initialize".equals(request.method());
        String requestedVersion = initialize
                ? DialectSupport.string(params, "protocolVersion", true)
                : transportProtocolVersion;
        if (requestedVersion == null) {
            requestedVersion = version();
        }
        if (!version().equals(requestedVersion)) {
            throw new cn.richie696.component.mcp.protocol.McpProtocolException(
                    "MCP_PROTOCOL_VERSION_MISMATCH",
                    -32022,
                    "Legacy request protocol version does not match selected dialect",
                    Map.of("expected", version(), "actual", requestedVersion));
        }

        McpImplementationInfo peer = initialize
                ? DialectSupport.implementation(params, "clientInfo", true)
                : null;
        Map<String, Object> capabilities = initialize
                ? DialectSupport.object(params, "capabilities", true)
                : Map.of();
        Map<String, Object> arguments = new LinkedHashMap<>(params);
        if (initialize) {
            arguments.remove("protocolVersion");
            arguments.remove("clientInfo");
            arguments.remove("capabilities");
        }
        return new McpNormalizedRequest(
                request.id(),
                request.method(),
                arguments,
                version(),
                era(),
                peer,
                capabilities,
                Map.of());
    }

    @Override
    public McpNormalizedResult normalizeResult(Map<String, Object> result) {
        Map<String, Object> payload = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        Object resultType = payload.remove("resultType");
        if (resultType == null || "complete".equals(resultType)) {
            return new McpNormalizedResult(McpNormalizedResult.ResultType.COMPLETE, payload);
        }
        throw DialectSupport.invalidParams("Unsupported resultType: " + resultType);
    }

    @Override
    public Map<String, Object> encodeResult(McpNormalizedResult result) {
        Map<String, Object> wire = new LinkedHashMap<>(result.payload());
        if (result.resultType() != McpNormalizedResult.ResultType.COMPLETE) {
            throw DialectSupport.invalidParams(
                    "Legacy protocol cannot encode resultType: " + result.resultType());
        }
        return Map.copyOf(wire);
    }
}
