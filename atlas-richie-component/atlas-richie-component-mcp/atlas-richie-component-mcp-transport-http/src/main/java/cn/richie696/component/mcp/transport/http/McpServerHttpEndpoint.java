package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.discovery.McpCacheScope;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoveryCodec;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.schema.McpSchemaDefinitionException;
import cn.richie696.component.mcp.server.dispatch.McpToolDispatcher;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-neutral modern MCP endpoint. A Spring/Netty adapter only maps HTTP values to this type.
 */
public final class McpServerHttpEndpoint {
    private final McpToolRegistry registry;
    private final McpToolDispatcher dispatcher;
    private final McpStreamableHttpRequestValidator requestValidator;
    private final McpImplementationInfo serverInfo;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final McpDiscoveryCodec discoveryCodec = new McpDiscoveryCodec();

    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo) {
        this(registry, serverInfo, origin -> true);
    }

    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dispatcher = new McpToolDispatcher(registry);
        this.requestValidator = new McpStreamableHttpRequestValidator(
                java.util.Set.of(McpProtocolVersions.V_2026_07_28),
                Objects.requireNonNull(originPolicy, "originPolicy"));
        this.serverInfo = Objects.requireNonNull(serverInfo, "serverInfo");
    }

    public McpHttpResponse handle(
            String jsonBody,
            Map<String, List<String>> headers) {
        McpHttpRequest request = new McpHttpRequest("POST", headers, parse(jsonBody));
        try {
            McpValidatedHttpRequest validated = requestValidator.validate(request);
            McpJsonRpcRequest message = validated.message();
            McpCallContext context = context(validated);
            if (message.notification()) {
                return McpHttpResponse.accepted();
            }
            Map<String, Object> result = switch (message.method()) {
                case "server/discover" -> discover();
                case "tools/list" -> toolsList(context);
                case "tools/call" -> toolsCall(message, context);
                case "ping" -> Map.of("resultType", "complete");
                default -> throw new McpProtocolException(
                        "MCP_METHOD_NOT_FOUND", -32601, "Method not found: " + message.method(), Map.of());
            };
            return McpHttpResponse.json(200, response(message.id(), result));
        } catch (McpHttpTransportException exception) {
            return exception.protocolError()
                    .<McpHttpResponse>map(error -> McpHttpResponse.json(
                            exception.httpStatus(), errorResponse(null, error(error))))
                    .orElseGet(() -> McpHttpResponse.json(exception.httpStatus(), Map.of()));
        } catch (McpProtocolException exception) {
            int status = exception.jsonRpcCode() == -32601 ? 404 : 400;
            return McpHttpResponse.json(status, errorResponse(request.message().id(), error(exception)));
        } catch (Exception exception) {
            return McpHttpResponse.json(500, errorResponse(request.message().id(),
                    error(new McpProtocolException(
                            "MCP_INTERNAL_ERROR", -32603, "Internal error", Map.of(), exception))));
        }
    }

    private McpJsonRpcRequest parse(String body) {
        try {
            Map<?, ?> raw = jsonMapper.readValue(body, Map.class);
            Object id = raw.get("id");
            Object method = raw.get("method");
            Map<String, Object> params = object(raw.get("params"));
            return new McpJsonRpcRequest(
                    raw.get("jsonrpc") instanceof String text ? text : null,
                    id,
                    method instanceof String text ? text : null,
                    params);
        } catch (JacksonException | ClassCastException exception) {
            return new McpJsonRpcRequest(null, null, null, Map.of());
        }
    }

    private McpCallContext context(McpValidatedHttpRequest request) {
        return new McpCallContext(
                String.valueOf(request.message().id()),
                request.protocolVersion(),
                "anonymous",
                "anonymous",
                Instant.now().plusSeconds(30),
                Map.of(),
                null,
                null);
    }

    private Map<String, Object> discover() {
        McpDiscoverResult result = new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                Map.of("tools", Map.of("listChanged", false)),
                serverInfo,
                null,
                60_000,
                McpCacheScope.PUBLIC,
                Map.of());
        return discoveryCodec.encodeResult(result);
    }

    private Map<String, Object> toolsList(McpCallContext context) {
        List<Map<String, Object>> tools = registry.snapshot(context).tools().stream()
                .map(this::wireTool)
                .toList();
        return Map.of("resultType", "complete", "tools", tools);
    }

    private Map<String, Object> toolsCall(McpJsonRpcRequest message, McpCallContext context) {
        String name = requiredString(message.params().get("name"), "params.name");
        Map<String, Object> arguments = object(message.params().get("arguments"));
        McpToolResponse response = dispatcher.dispatch(name, arguments, context)
                .toCompletableFuture()
                .join();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", "complete");
        result.put("content", response.content());
        if (response.structuredContent() != null) {
            result.put("structuredContent", response.structuredContent());
        }
        if (response.error()) {
            result.put("isError", true);
        }
        return result;
    }

    private Map<String, Object> wireTool(McpToolDescriptor descriptor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", descriptor.name());
        if (descriptor.title() != null) {
            result.put("title", descriptor.title());
        }
        if (descriptor.description() != null) {
            result.put("description", descriptor.description());
        }
        result.put("inputSchema", descriptor.inputSchema());
        if (!descriptor.outputSchema().isEmpty()) {
            result.put("outputSchema", descriptor.outputSchema());
        }
        if (!descriptor.annotations().isEmpty()) {
            result.put("annotations", descriptor.annotations());
        }
        return result;
    }

    private Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> errorResponse(Object id, Map<String, Object> error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    private Map<String, Object> error(McpProtocolException exception) {
        return Map.of(
                "code", exception.jsonRpcCode(),
                "message", exception.getMessage(),
                "data", exception.data());
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entry) -> {
            if (key instanceof String text) {
                result.put(text, entry);
            }
        });
        return result;
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new McpProtocolException("MCP_INVALID_PARAMS", -32602, field + " must be non-blank", Map.of());
        }
        return text;
    }
}
