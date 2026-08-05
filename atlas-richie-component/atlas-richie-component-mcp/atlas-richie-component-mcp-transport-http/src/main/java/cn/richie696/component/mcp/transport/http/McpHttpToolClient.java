package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcError;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Modern MCP client transport. HTTP, JSON-RPC and protocol metadata stay inside this adapter.
 */
public final class McpHttpToolClient {
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpHttpToolClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Duration.ofSeconds(30));
    }

    public McpHttpToolClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public List<McpRemoteTool> listTools(URI endpoint, Map<String, String> headers) {
        Map<String, Object> result = exchange(endpoint, "tools/list", Map.of(), headers);
        Object rawTools = result.get("tools");
        if (!(rawTools instanceof List<?> tools)) {
            throw clientFailure("MCP tools/list result.tools must be an array", 200, null, null);
        }
        List<McpRemoteTool> resolved = new ArrayList<>(tools.size());
        for (Object rawTool : tools) {
            if (!(rawTool instanceof Map<?, ?> raw)) {
                throw clientFailure("MCP tools/list contains a non-object tool", 200, null, null);
            }
            resolved.add(new McpRemoteTool(
                    requiredString(raw.get("name"), "tools[].name"),
                    optionalString(raw.get("title"), "tools[].title"),
                    optionalString(raw.get("description"), "tools[].description"),
                    object(raw.get("inputSchema"), "tools[].inputSchema"),
                    object(raw.get("outputSchema"), "tools[].outputSchema"),
                    object(raw.get("annotations"), "tools[].annotations")));
        }
        return List.copyOf(resolved);
    }

    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        Map<String, Object> result = exchange(
                endpoint,
                "tools/call",
                Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments),
                headers);
        Object contentValue = result.get("content");
        List<Map<String, Object>> content = objectList(contentValue, "tools/call result.content");
        return new McpToolResponse(content, result.get("structuredContent"), Boolean.TRUE.equals(result.get("isError")));
    }

    private Map<String, Object> exchange(
            URI endpoint,
            String method,
            Map<String, Object> params,
            Map<String, String> extraHeaders) {
        URI requestUri = normalizeEndpoint(endpoint);
        String id = UUID.randomUUID().toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28);
        metadata.put(McpMetaKeys.CLIENT_INFO, Map.of("name", "atlas-richie-mcp-client", "version", "1.0.0"));
        metadata.put(McpMetaKeys.CLIENT_CAPABILITIES, Map.of());
        Map<String, Object> bodyParams = new LinkedHashMap<>(params);
        bodyParams.put("_meta", metadata);
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", bodyParams);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                    .timeout(requestTimeout)
                    .header(McpHttpHeaders.CONTENT_TYPE, "application/json")
                    .header(McpHttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .header(McpHttpHeaders.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28)
                    .header(McpHttpHeaders.METHOD, method);
            if (method.equals("tools/call")) {
                builder.header(McpHttpHeaders.NAME, String.valueOf(params.get("name")));
            }
            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(writeJson(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = readEnvelope(response.body(), response.statusCode(), response.headers().map());
            Object error = envelope.get("error");
            if (error instanceof Map<?, ?> rawError) {
                throw protocolFailure(response.statusCode(), rawError, response.headers().map());
            }
            Object result = envelope.get("result");
            if (!(result instanceof Map<?, ?> rawResult)) {
                throw clientFailure("MCP response is missing result", response.statusCode(), null, null);
            }
            Map<String, Object> typed = new LinkedHashMap<>();
            rawResult.forEach((key, value) -> {
                if (key instanceof String text) {
                    typed.put(text, value);
                }
            });
            return typed;
        } catch (McpHttpClientException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw clientFailure("MCP HTTP request interrupted", 0, null, exception);
        } catch (Exception exception) {
            throw clientFailure("MCP HTTP request failed", 0, null, exception);
        }
    }

    private Map<String, Object> readEnvelope(String body, int status, Map<String, List<String>> responseHeaders) {
        String json = body;
        if (body != null && body.contains("data:")) {
            String[] lines = body.split("\\R");
            for (int index = lines.length - 1; index >= 0; index--) {
                if (lines[index].startsWith("data:")) {
                    json = lines[index].substring("data:".length()).strip();
                    break;
                }
            }
        }
        try {
            Map<?, ?> raw = jsonMapper.readValue(json, Map.class);
            Map<String, Object> envelope = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key instanceof String text) {
                    envelope.put(text, value);
                }
            });
            if (status < 200 || status >= 300) {
                Object error = envelope.get("error");
                if (error instanceof Map<?, ?> rawError) {
                    throw protocolFailure(status, rawError, responseHeaders);
                }
                throw clientFailure("MCP HTTP server returned status " + status, status, null, null, responseHeaders);
            }
            return envelope;
        } catch (McpHttpClientException exception) {
            throw exception;
        } catch (JacksonException | ClassCastException exception) {
            throw clientFailure("MCP HTTP response is not valid JSON-RPC", status, null, exception, responseHeaders);
        }
    }

    private McpHttpClientException protocolFailure(
            int status,
            Map<?, ?> rawError,
            Map<String, List<String>> responseHeaders) {
        int code = rawError.get("code") instanceof Number number ? number.intValue() : -32603;
        String message = rawError.get("message") instanceof String text ? text : "MCP protocol error";
        McpProtocolException protocol = new McpProtocolException(
                "MCP_REMOTE_PROTOCOL_ERROR", code, message, object(rawError.get("data"), "error.data"));
        return clientFailure(message, status, protocol, null, responseHeaders);
    }

    private URI normalizeEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        String path = endpoint.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            String normalizedPath = (path == null || path.isBlank() ? "" : path) + "/mcp";
            try {
                return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(), endpoint.getPort(),
                        normalizedPath, endpoint.getQuery(), endpoint.getFragment());
            } catch (Exception exception) {
                throw clientFailure("Invalid MCP endpoint", 0, null, exception);
            }
        }
        return endpoint;
    }

    private String writeJson(Object value) throws JacksonException {
        return jsonMapper.writeValueAsString(value);
    }

    private Map<String, Object> object(Object value, String field) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw clientFailure(field + " must be an object", 200, null, null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entry) -> {
            if (key instanceof String text) {
                result.put(text, entry);
            }
        });
        return result;
    }

    private List<Map<String, Object>> objectList(Object value, String field) {
        if (!(value instanceof List<?> raw)) {
            throw clientFailure(field + " must be an array", 200, null, null);
        }
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            result.add(object(entry, field + "[]"));
        }
        return List.copyOf(result);
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw clientFailure(field + " must be a non-blank string", 200, null, null);
        }
        return text;
    }

    private String optionalString(Object value, String field) {
        return value == null ? "" : requiredString(value, field);
    }

    private McpHttpClientException clientFailure(String message, int status, McpProtocolException error, Throwable cause) {
        return new McpHttpClientException(message, status, error, cause);
    }

    private McpHttpClientException clientFailure(
            String message,
            int status,
            McpProtocolException error,
            Throwable cause,
            Map<String, List<String>> responseHeaders) {
        return new McpHttpClientException(message, status, error, cause, responseHeaders);
    }
}
