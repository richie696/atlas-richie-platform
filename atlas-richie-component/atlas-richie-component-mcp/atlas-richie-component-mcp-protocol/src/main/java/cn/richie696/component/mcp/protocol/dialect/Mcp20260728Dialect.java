package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpJsonRpcValidator;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2026-07-28 无状态协议时代适配器。
 */
public final class Mcp20260728Dialect implements McpProtocolDialect {
    @Override
    public String version() {
        return McpProtocolVersions.V_2026_07_28;
    }

    @Override
    public McpProtocolEra era() {
        return McpProtocolEra.STATELESS_2026;
    }

    @Override
    public McpNormalizedRequest normalizeRequest(McpJsonRpcRequest request, String transportProtocolVersion) {
        McpJsonRpcValidator.validate(request);
        Map<String, Object> metadata = DialectSupport.object(request.params(), "_meta", true);
        String metadataVersion = DialectSupport.string(metadata, McpMetaKeys.PROTOCOL_VERSION, true);
        if (!version().equals(metadataVersion)) {
            throw unsupportedVersion(metadataVersion);
        }
        if (transportProtocolVersion != null && !metadataVersion.equals(transportProtocolVersion)) {
            throw headerMismatch(metadataVersion, transportProtocolVersion);
        }

        McpImplementationInfo peer = DialectSupport.implementation(metadata, McpMetaKeys.CLIENT_INFO, false);
        Map<String, Object> capabilities =
                DialectSupport.object(metadata, McpMetaKeys.CLIENT_CAPABILITIES, true);
        Map<String, Object> arguments = new LinkedHashMap<>(request.params());
        arguments.remove("_meta");
        return new McpNormalizedRequest(
                request.id(),
                request.method(),
                arguments,
                version(),
                era(),
                peer,
                capabilities,
                metadata);
    }

    @Override
    public McpNormalizedResult normalizeResult(Map<String, Object> result) {
        Map<String, Object> payload = result == null ? Map.of() : new LinkedHashMap<>(result);
        String resultType = DialectSupport.string(payload, "resultType", true);
        payload.remove("resultType");
        return new McpNormalizedResult(parseResultType(resultType), payload);
    }

    @Override
    public Map<String, Object> encodeResult(McpNormalizedResult result) {
        Map<String, Object> wire = new LinkedHashMap<>(result.payload());
        wire.put("resultType", switch (result.resultType()) {
            case COMPLETE -> "complete";
            case INPUT_REQUIRED -> "input_required";
        });
        return Map.copyOf(wire);
    }

    private McpNormalizedResult.ResultType parseResultType(String value) {
        return switch (value) {
            case "complete" -> McpNormalizedResult.ResultType.COMPLETE;
            case "input_required" -> McpNormalizedResult.ResultType.INPUT_REQUIRED;
            default -> throw DialectSupport.invalidParams("Unsupported resultType: " + value);
        };
    }

    private McpProtocolException unsupportedVersion(String actual) {
        return new McpProtocolException(
                "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                -32022,
                "Unsupported protocol version",
                Map.of("supported", java.util.List.of(version()), "requested", actual));
    }

    private McpProtocolException headerMismatch(String metadataVersion, String headerVersion) {
        return new McpProtocolException(
                "MCP_HEADER_MISMATCH",
                -32020,
                "MCP-Protocol-Version header does not match request metadata",
                Map.of("metadata", metadataVersion, "header", headerVersion));
    }
}
