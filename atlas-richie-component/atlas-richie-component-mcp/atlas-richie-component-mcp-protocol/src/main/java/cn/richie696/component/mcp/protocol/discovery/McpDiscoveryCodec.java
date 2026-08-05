package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.dialect.Mcp20260728Dialect;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 2026-07-28 server/discover 线模型编解码器。
 */
public final class McpDiscoveryCodec {
    public static final String METHOD = "server/discover";

    private final Mcp20260728Dialect dialect;

    public McpDiscoveryCodec() {
        this(new Mcp20260728Dialect());
    }

    public McpDiscoveryCodec(Mcp20260728Dialect dialect) {
        this.dialect = dialect;
    }

    public McpJsonRpcRequest encodeRequest(
            Object id,
            McpImplementationInfo clientInfo,
            Map<String, Object> clientCapabilities) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28);
        metadata.put(McpMetaKeys.CLIENT_CAPABILITIES,
                clientCapabilities == null ? Map.of() : clientCapabilities);
        if (clientInfo != null) {
            metadata.put(McpMetaKeys.CLIENT_INFO, clientInfo.toWire());
        }
        return new McpJsonRpcRequest("2.0", id, METHOD, Map.of("_meta", metadata));
    }

    public McpNormalizedRequest decodeRequest(
            McpJsonRpcRequest request,
            String transportProtocolVersion) {
        McpNormalizedRequest normalized = dialect.normalizeRequest(request, transportProtocolVersion);
        if (!METHOD.equals(normalized.method())) {
            throw invalidParams("Expected method " + METHOD + " but received " + normalized.method());
        }
        if (!normalized.arguments().isEmpty()) {
            throw invalidParams("server/discover accepts no parameters beyond _meta");
        }
        if (normalized.notification()) {
            throw invalidParams("server/discover must be a request with an id");
        }
        return normalized;
    }

    public Map<String, Object> encodeResult(McpDiscoverResult result) {
        Map<String, Object> payload = new LinkedHashMap<>(result.extensions());
        payload.put("supportedVersions", result.supportedVersions());
        payload.put("capabilities", result.capabilities());
        if (result.serverInfo() != null) {
            payload.put("_meta", Map.of(McpMetaKeys.SERVER_INFO, result.serverInfo().toWire()));
        }
        if (result.instructions() != null) {
            payload.put("instructions", result.instructions());
        }
        payload.put("ttlMs", result.ttlMs());
        payload.put("cacheScope", result.cacheScope().wireValue());
        return dialect.encodeResult(
                new McpNormalizedResult(McpNormalizedResult.ResultType.COMPLETE, payload));
    }

    public McpDiscoverResult decodeResult(Map<String, Object> wireResult) {
        McpNormalizedResult normalized = dialect.normalizeResult(wireResult);
        if (normalized.resultType() != McpNormalizedResult.ResultType.COMPLETE) {
            throw invalidParams("server/discover must return resultType complete");
        }

        Map<String, Object> payload = new LinkedHashMap<>(normalized.payload());
        List<String> versions = stringList(payload.remove("supportedVersions"), "supportedVersions");
        Map<String, Object> capabilities = object(payload.remove("capabilities"), "capabilities", true);
        Map<String, Object> metadata = object(payload.remove("_meta"), "_meta", false);
        McpImplementationInfo serverInfo = implementation(
                metadata.get(McpMetaKeys.SERVER_INFO),
                McpMetaKeys.SERVER_INFO,
                false);
        String instructions = optionalString(payload.remove("instructions"), "instructions");
        long ttlMs = nonNegativeInteger(payload.remove("ttlMs"), "ttlMs");
        McpCacheScope cacheScope;
        try {
            cacheScope = McpCacheScope.fromWireValue(
                    requiredString(payload.remove("cacheScope"), "cacheScope"));
        } catch (IllegalArgumentException exception) {
            throw invalidParams(exception.getMessage());
        }
        return new McpDiscoverResult(
                versions,
                capabilities,
                serverInfo,
                instructions,
                ttlMs,
                cacheScope,
                payload);
    }

    private List<String> stringList(Object value, String field) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            throw invalidParams(field + " must be a non-empty array");
        }
        List<String> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            result.add(requiredString(entry, field + "[]"));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> object(Object value, String field, boolean required) {
        if (value == null && !required) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw invalidParams(field + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entryValue) -> {
            if (!(key instanceof String stringKey)) {
                throw invalidParams(field + " contains a non-string key");
            }
            result.put(stringKey, entryValue);
        });
        return Collections.unmodifiableMap(result);
    }

    private McpImplementationInfo implementation(Object value, String field, boolean required) {
        if (value == null && !required) {
            return null;
        }
        Map<String, Object> object = object(value, field, required);
        try {
            return McpImplementationInfo.fromWire(object);
        } catch (IllegalArgumentException exception) {
            throw invalidParams(field + ": " + exception.getMessage());
        }
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw invalidParams(field + " must be a non-blank string");
        }
        return string;
    }

    private String optionalString(Object value, String field) {
        return value == null ? null : requiredString(value, field);
    }

    private long nonNegativeInteger(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw invalidParams(field + " must be a number");
        }
        double decimal = number.doubleValue();
        long result = number.longValue();
        if (!Double.isFinite(decimal) || decimal < 0 || decimal != result) {
            throw invalidParams(field + " must be a non-negative integer");
        }
        return result;
    }

    private McpProtocolException invalidParams(String message) {
        return new McpProtocolException("MCP_INVALID_PARAMS", -32602, message, Map.of());
    }
}
