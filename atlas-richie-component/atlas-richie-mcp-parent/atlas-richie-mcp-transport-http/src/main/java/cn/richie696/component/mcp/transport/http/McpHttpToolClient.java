package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoveryCodec;
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
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private final int maxPages;
    private final int maxItems;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpHttpToolClient() {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(30),
                "atlas-richie-mcp-client",
                "1.0.0",
                McpProtocolVersions.V_2026_07_28,
                100,
                10_000);
    }

    public McpHttpToolClient(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, "atlas-richie-mcp-client", "1.0.0",
                McpProtocolVersions.V_2026_07_28, 100, 10_000);
    }

    public McpHttpToolClient(
            HttpClient httpClient,
            Duration requestTimeout,
            String clientName,
            String clientVersion) {
        this(httpClient, requestTimeout, clientName, clientVersion, McpProtocolVersions.V_2026_07_28);
    }

    public McpHttpToolClient(
            HttpClient httpClient,
            Duration requestTimeout,
            String clientName,
            String clientVersion,
            String protocolVersion) {
        this(httpClient, requestTimeout, clientName, clientVersion, protocolVersion, 100, 10_000);
    }

    public McpHttpToolClient(
            HttpClient httpClient,
            Duration requestTimeout,
            String clientName,
            String clientVersion,
            String protocolVersion,
            int maxPages,
            int maxItems) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.clientName = requiredClientValue(clientName, "clientName");
        this.clientVersion = requiredClientValue(clientVersion, "clientVersion");
        if (!McpProtocolVersions.SUPPORTED.contains(protocolVersion)) {
            throw new IllegalArgumentException("Unsupported MCP protocol version: " + protocolVersion);
        }
        this.protocolVersion = protocolVersion;
        if (maxPages < 1 || maxItems < 1) throw new IllegalArgumentException("MCP pagination limits must be positive");
        this.maxPages = maxPages;
        this.maxItems = maxItems;
    }

    public McpHttpToolClient forProtocolVersion(String version) {
        return new McpHttpToolClient(httpClient, requestTimeout, clientName, clientVersion, version, maxPages, maxItems);
    }

    public String protocolVersion() {
        return protocolVersion;
    }

    public McpDiscoverResult discover(URI endpoint, Map<String, String> headers) {
        return new McpDiscoveryCodec().decodeResult(exchange(endpoint, McpDiscoveryCodec.METHOD, Map.of(), headers));
    }

    public List<McpRemoteTool> listTools(URI endpoint, Map<String, String> headers) {
        List<McpRemoteTool> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP tools/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(endpoint, "tools/list", pageParams(cursor), headers);
            Object rawTools = result.get("tools");
            if (!(rawTools instanceof List<?> tools)) {
                throw clientFailure("MCP tools/list result.tools must be an array", 200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP tools/list exceeded maxItems", 200, null, null);
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
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        return callTool(endpoint, toolName, arguments, headers, Map.of(), null);
    }

    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers,
            Map<String, Object> inputResponses,
            String requestState) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        if (inputResponses != null && !inputResponses.isEmpty()) params.put("inputResponses", inputResponses);
        if (requestState != null && !requestState.isBlank()) params.put("requestState", requestState);
        Map<String, Object> result = exchange(
                endpoint,
                "tools/call",
                params,
                headers);
        Object contentValue = result.get("content");
        List<Map<String, Object>> content = objectList(contentValue, "tools/call result.content");
        return new McpToolResponse(
                content,
                result.get("structuredContent"),
                Boolean.TRUE.equals(result.get("isError")),
                optionalString(result.get("resultType"), "tools/call result.resultType"),
                object(result.get("inputRequests"), "tools/call result.inputRequests"),
                optionalString(result.get("requestState"), "tools/call result.requestState"));
    }

    public List<McpResourceDescriptor> listResources(URI endpoint, Map<String, String> headers) {
        List<McpResourceDescriptor> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP resources/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(endpoint, "resources/list", pageParams(cursor), headers);
            Object rawResources = result.get("resources");
            if (!(rawResources instanceof List<?> resources)) {
                throw clientFailure("MCP resources/list result.resources must be an array", 200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP resources/list exceeded maxItems", 200, null, null);
            for (Object rawValue : resources) {
                Map<String, Object> raw = object(rawValue, "resources[]");
                resolved.add(new McpResourceDescriptor(
                        requiredString(raw.get("uri"), "resources[].uri"),
                        requiredString(raw.get("name"), "resources[].name"),
                        optionalString(raw.get("title"), "resources[].title"),
                        optionalString(raw.get("description"), "resources[].description"),
                        optionalString(raw.get("mimeType"), "resources[].mimeType"),
                        raw.get("size") instanceof Number number ? number.longValue() : null,
                        optionalObjectList(raw.get("icons"), "resources[].icons"),
                        object(raw.get("annotations"), "resources[].annotations")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    public List<McpResourceTemplateDescriptor> listResourceTemplates(
            URI endpoint,
            Map<String, String> headers) {
        List<McpResourceTemplateDescriptor> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP resourceTemplates/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(
                    endpoint, "resources/templates/list", pageParams(cursor), headers);
            Object rawTemplates = result.get("resourceTemplates");
            if (!(rawTemplates instanceof List<?> templates)) {
                throw clientFailure(
                        "MCP resources/templates/list result.resourceTemplates must be an array",
                        200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP resourceTemplates/list exceeded maxItems", 200, null, null);
            for (Object rawValue : templates) {
                Map<String, Object> raw = object(rawValue, "resourceTemplates[]");
                resolved.add(new McpResourceTemplateDescriptor(
                        requiredString(raw.get("uriTemplate"), "resourceTemplates[].uriTemplate"),
                        requiredString(raw.get("name"), "resourceTemplates[].name"),
                        optionalString(raw.get("title"), "resourceTemplates[].title"),
                        optionalString(raw.get("description"), "resourceTemplates[].description"),
                        optionalString(raw.get("mimeType"), "resourceTemplates[].mimeType"),
                        optionalObjectList(raw.get("icons"), "resourceTemplates[].icons"),
                        object(raw.get("annotations"), "resourceTemplates[].annotations")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    public McpResourceContent readResource(
            URI endpoint,
            String uri,
            Map<String, String> headers) {
        Map<String, Object> result = exchange(endpoint, "resources/read", Map.of("uri", uri), headers);
        return new McpResourceContent(objectList(result.get("contents"), "resources/read result.contents"));
    }

    public List<McpPromptDescriptor> listPrompts(URI endpoint, Map<String, String> headers) {
        List<McpPromptDescriptor> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP prompts/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(endpoint, "prompts/list", pageParams(cursor), headers);
            Object rawPrompts = result.get("prompts");
            if (!(rawPrompts instanceof List<?> prompts)) {
                throw clientFailure("MCP prompts/list result.prompts must be an array", 200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP prompts/list exceeded maxItems", 200, null, null);
            for (Object rawValue : prompts) {
                Map<String, Object> raw = object(rawValue, "prompts[]");
                resolved.add(new McpPromptDescriptor(
                        requiredString(raw.get("name"), "prompts[].name"),
                        optionalString(raw.get("title"), "prompts[].title"),
                        optionalString(raw.get("description"), "prompts[].description"),
                        optionalObjectList(raw.get("arguments"), "prompts[].arguments")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    public McpPromptContent getPrompt(
            URI endpoint,
            String name,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        Map<String, Object> result = exchange(
                endpoint,
                "prompts/get",
                Map.of("name", name, "arguments", arguments == null ? Map.of() : arguments),
                headers);
        return new McpPromptContent(
                optionalString(result.get("description"), "prompts/get result.description"),
                objectList(result.get("messages"), "prompts/get result.messages"));
    }

    public McpCompletionResult complete(
            URI endpoint,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> headers,
            Map<String, String> contextArguments) {
        Map<String, Object> context = contextArguments == null || contextArguments.isEmpty()
                ? Map.of() : Map.of("arguments", contextArguments);
        Map<String, Object> result = exchange(endpoint, "completion/complete", Map.of(
                "ref", reference == null ? Map.of() : reference,
                "argument", Map.of("name", argumentName, "value", value),
                "context", context), headers);
        Map<String, Object> completion = object(result.get("completion"), "completion");
        Object values = completion.get("values");
        if (!(values instanceof List<?> list)) {
            throw clientFailure("completion.values must be an array", 200, null, null);
        }
        List<String> textValues = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        return new McpCompletionResult(textValues,
                completion.get("total") instanceof Number number ? number.intValue() : null,
                Boolean.TRUE.equals(completion.get("hasMore")));
    }

    private Map<String, Object> exchange(
            URI endpoint,
            String method,
            Map<String, Object> params,
            Map<String, String> extraHeaders) {
        URI requestUri = normalizeEndpoint(endpoint);
        String id = UUID.randomUUID().toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.PROTOCOL_VERSION, protocolVersion);
        metadata.put(McpMetaKeys.CLIENT_INFO, Map.of("name", clientName, "version", clientVersion));
        metadata.put(McpMetaKeys.CLIENT_CAPABILITIES, Map.of());
        Map<String, Object> bodyParams = new LinkedHashMap<>(params);
        bodyParams.put("_meta", metadata);
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", bodyParams);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                    .timeout(requestTimeout)
                    .header(McpHttpHeaders.CONTENT_TYPE, "application/json")
                    .header(McpHttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .header(McpHttpHeaders.PROTOCOL_VERSION, protocolVersion)
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

    private Map<String, Object> pageParams(String cursor) {
        return cursor == null ? Map.of() : Map.of("cursor", cursor);
    }

    private String nextCursor(Map<String, Object> result) {
        Object value = result.get("nextCursor");
        return value == null ? null : requiredString(value, "nextCursor");
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

    private List<Map<String, Object>> optionalObjectList(Object value, String field) {
        return value == null ? List.of() : objectList(value, field);
    }

    private String requiredClientValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
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
