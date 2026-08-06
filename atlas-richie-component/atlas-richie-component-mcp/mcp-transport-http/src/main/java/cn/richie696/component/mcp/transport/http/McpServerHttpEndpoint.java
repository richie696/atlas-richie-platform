package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.McpProgressReporter;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.server.McpCompletionRequest;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.discovery.McpCacheScope;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoveryCodec;
import cn.richie696.component.mcp.protocol.discovery.McpCacheHints;
import cn.richie696.component.mcp.protocol.pagination.McpCursorCodec;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.schema.McpSchemaDefinitionException;
import cn.richie696.component.mcp.server.dispatch.McpToolDispatcher;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceRegistration;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistry;
import cn.richie696.component.mcp.server.completion.McpCompletionRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.nio.charset.StandardCharsets;

/**
 * Framework-neutral modern MCP endpoint. A Spring/Netty adapter only maps HTTP values to this type.
 */
public final class McpServerHttpEndpoint {
    private final McpToolRegistry registry;
    private final McpToolDispatcher dispatcher;
    private final McpStreamableHttpRequestValidator requestValidator;
    private final McpImplementationInfo serverInfo;
    private final McpResourceRegistry resourceRegistry;
    private final McpPromptRegistry promptRegistry;
    private final McpCompletionRegistry completionRegistry;
    private final McpCursorCodec cursorCodec;
    private final McpCancellationRegistry cancellationRegistry;
    private final McpSubscriptionManager subscriptionManager;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final McpDiscoveryCodec discoveryCodec = new McpDiscoveryCodec();

    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo) {
        this(registry, serverInfo, origin -> true, new McpResourceRegistry(), new McpPromptRegistry(), null);
    }

    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy) {
        this(registry, serverInfo, originPolicy, new McpResourceRegistry(), new McpPromptRegistry(), null);
    }

    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry) {
        this(registry, serverInfo, originPolicy, resourceRegistry, promptRegistry, null);
    }

    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            McpCompletionRegistry completionRegistry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dispatcher = new McpToolDispatcher(registry);
        this.requestValidator = new McpStreamableHttpRequestValidator(
                java.util.Set.of(McpProtocolVersions.V_2026_07_28),
                Objects.requireNonNull(originPolicy, "originPolicy"));
        this.serverInfo = Objects.requireNonNull(serverInfo, "serverInfo");
        this.resourceRegistry = Objects.requireNonNull(resourceRegistry, "resourceRegistry");
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
        this.completionRegistry = completionRegistry;
        this.cursorCodec = new McpCursorCodec(
                ("atlas-richie-mcp-cursor:" + serverInfo.name()).getBytes(StandardCharsets.UTF_8));
        this.cancellationRegistry = new McpCancellationRegistry();
        this.subscriptionManager = new McpSubscriptionManager();
    }

    public McpHttpResponse handle(
            String jsonBody,
            Map<String, List<String>> headers) {
        McpHttpRequest request = new McpHttpRequest("POST", headers, parse(jsonBody));
        String activeRequestId = null;
        try {
            McpValidatedHttpRequest validated = requestValidator.validate(request);
            McpJsonRpcRequest message = validated.message();
            if ("notifications/cancelled".equals(message.method())) {
                cancellationRegistry.cancel(message.params().get("requestId"));
                return McpHttpResponse.accepted();
            }
            if ("subscriptions/listen".equals(message.method())) {
                return openSubscription(message);
            }
            String requestId = String.valueOf(message.id());
            activeRequestId = requestId;
            McpCancellationToken cancellationToken = cancellationRegistry.begin(requestId);
            List<Map<String, Object>> notifications = new java.util.concurrent.CopyOnWriteArrayList<>();
            McpCallContext context = context(validated, cancellationToken, notifications);
            if (message.notification()) {
                cancellationRegistry.finish(requestId);
                return McpHttpResponse.accepted();
            }
            Map<String, Object> result = switch (message.method()) {
                case "server/discover" -> discover();
                case "tools/list" -> toolsList(message.params(), context);
                case "tools/call" -> toolsCall(message, context);
                case "resources/list" -> resourcesList(message.params(), context);
                case "resources/templates/list" -> resourceTemplatesList(message.params());
                case "resources/read" -> resourcesRead(message, context);
                case "prompts/list" -> promptsList(message.params());
                case "prompts/get" -> promptsGet(message, context);
                case "completion/complete" -> completion(message, context);
                case "ping" -> Map.of("resultType", "complete");
                default -> throw new McpProtocolException(
                        "MCP_METHOD_NOT_FOUND", -32601, "Method not found: " + message.method(), Map.of());
            };
            Map<String, Object> response = response(message.id(), result);
            return notifications.isEmpty()
                    ? McpHttpResponse.json(200, response)
                    : McpHttpResponse.sse(200, response, notifications);
        } catch (McpHttpTransportException exception) {
            return exception.protocolError()
                    .<McpHttpResponse>map(error -> McpHttpResponse.json(
                            exception.httpStatus(), errorResponse(null, error(error))))
                    .orElseGet(() -> McpHttpResponse.json(exception.httpStatus(), Map.of()));
        } catch (McpProtocolException exception) {
            int status = exception.jsonRpcCode() == -32601 ? 404 : 400;
            return McpHttpResponse.json(status, errorResponse(request.message().id(), error(exception)));
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof McpProtocolException protocolException) {
                return McpHttpResponse.json(400,
                        errorResponse(request.message().id(), error(protocolException)));
            }
            return McpHttpResponse.json(500, errorResponse(request.message().id(),
                    error(new McpProtocolException("MCP_INTERNAL_ERROR", -32603,
                            "Internal error", Map.of(), cause == null ? exception : cause))));
        } catch (Exception exception) {
            return McpHttpResponse.json(500, errorResponse(request.message().id(),
                    error(new McpProtocolException(
                            "MCP_INTERNAL_ERROR", -32603, "Internal error", Map.of(), exception))));
        } finally {
            if (activeRequestId != null) {
                cancellationRegistry.finish(activeRequestId);
            }
        }
    }

    public McpSubscriptionManager subscriptionManager() {
        return subscriptionManager;
    }

    private McpHttpResponse openSubscription(McpJsonRpcRequest message) {
        if (!(message.params().get("notifications") instanceof Map<?, ?> raw)) {
            throw new McpProtocolException("MCP_INVALID_PARAMS", -32602,
                    "subscriptions/listen requires params.notifications", Map.of());
        }
        java.util.Set<String> resourceUris = new java.util.LinkedHashSet<>();
        Object rawResources = raw.get("resourceSubscriptions");
        if (rawResources instanceof List<?> list) {
            for (Object value : list) {
                resourceUris.add(requiredString(value, "notifications.resourceSubscriptions[]"));
            }
        }
        boolean tools = Boolean.TRUE.equals(raw.get("toolsListChanged"));
        boolean prompts = Boolean.TRUE.equals(raw.get("promptsListChanged"));
        boolean resources = Boolean.TRUE.equals(raw.get("resourcesListChanged"));
        McpSubscriptionManager.Subscription subscription = subscriptionManager.open(
                String.valueOf(message.id()),
                new McpSubscriptionSpec(tools, prompts, resources, resourceUris));
        Map<String, Object> accepted = Map.of(
                "jsonrpc", "2.0",
                "method", "notifications/subscriptions/acknowledged",
                "params", Map.of(
                        "_meta", Map.of(McpMetaKeys.SUBSCRIPTION_ID, String.valueOf(message.id())),
                        "notifications", raw));
        return McpHttpResponse.sse(200, subscription, List.of(accepted));
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

    private McpCallContext context(
            McpValidatedHttpRequest request,
            McpCancellationToken cancellationToken,
            List<Map<String, Object>> notifications) {
        Object progressToken = request.message().params().get("progressToken");
        if (progressToken == null && request.message().params().get("_meta") instanceof Map<?, ?> meta) {
            progressToken = meta.get(McpMetaKeys.PROGRESS_TOKEN);
        }
        Object finalProgressToken = progressToken;
        McpProgressReporter progressReporter = finalProgressToken == null
                ? McpProgressReporter.NOOP
                : new ProgressCollector(finalProgressToken, notifications);
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (request.message().params().get("inputResponses") instanceof Map<?, ?> inputResponses) {
            attributes.put("inputResponses", object(inputResponses));
        }
        if (request.message().params().get("requestState") instanceof String requestState) {
            attributes.put("requestState", requestState);
        }
        return new McpCallContext(
                String.valueOf(request.message().id()),
                request.protocolVersion(),
                "anonymous",
                "anonymous",
                Instant.now().plusSeconds(30),
                attributes,
                cancellationToken,
                progressReporter);
    }

    private static final class ProgressCollector implements McpProgressReporter {
        private final Object token;
        private final List<Map<String, Object>> notifications;
        private double lastProgress = Double.NEGATIVE_INFINITY;

        private ProgressCollector(Object token, List<Map<String, Object>> notifications) {
            this.token = token;
            this.notifications = notifications;
        }

        @Override
        public synchronized void report(double progress, Double total, String message) {
            if (!Double.isFinite(progress) || progress < lastProgress) {
                throw new IllegalArgumentException("MCP progress must be finite and monotonic");
            }
            lastProgress = progress;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("progressToken", token);
            params.put("progress", progress);
            if (total != null) params.put("total", total);
            if (message != null && !message.isBlank()) params.put("message", message);
            notifications.add(Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/progress",
                    "params", params));
        }
    }

    private Map<String, Object> discover() {
        McpDiscoverResult result = new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                capabilities(),
                serverInfo,
                null,
                60_000,
                McpCacheScope.PUBLIC,
                Map.of());
        return discoveryCodec.encodeResult(result);
    }

    private Map<String, Object> toolsList(Map<String, Object> params, McpCallContext context) {
        List<Map<String, Object>> tools = registry.snapshot(context).tools().stream()
                .map(this::wireTool)
                .toList();
        return pageResult("tools", tools, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    private Map<String, Object> resourcesList(Map<String, Object> params, McpCallContext context) {
        List<Map<String, Object>> resources = resourceRegistry.list(context).stream()
                .map(this::wireResource)
                .toList();
        return pageResult("resources", resources, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    private Map<String, Object> resourceTemplatesList(Map<String, Object> params) {
        List<Map<String, Object>> templates = resourceRegistry.listTemplates().stream()
                .map(this::wireResourceTemplate)
                .toList();
        return pageResult("resourceTemplates", templates, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    private Map<String, Object> resourcesRead(McpJsonRpcRequest message, McpCallContext context) {
        String uri = requiredString(message.params().get("uri"), "params.uri");
        McpResourceRegistration registration = resourceRegistry.resolve(uri, context);
        McpResourceContent content = registration.handler().read(uri, context)
                .toCompletableFuture().join();
        return McpCacheHints.add(Map.of("resultType", "complete", "contents", content.contents()), 0,
                McpCacheScope.PRIVATE);
    }

    private Map<String, Object> promptsList(Map<String, Object> params) {
        List<Map<String, Object>> prompts = promptRegistry.list().stream()
                .map(this::wirePrompt)
                .toList();
        return pageResult("prompts", prompts, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    private Map<String, Object> promptsGet(McpJsonRpcRequest message, McpCallContext context) {
        String name = requiredString(message.params().get("name"), "params.name");
        Map<String, Object> arguments = object(message.params().get("arguments"));
        McpPromptRegistry registrationRegistry = promptRegistry;
        var registration = registrationRegistry.resolve(name, arguments);
        McpPromptContent content = registration.handler().get(arguments, context)
                .toCompletableFuture().join();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", "complete");
        if (content.description() != null) {
            result.put("description", content.description());
        }
        result.put("messages", content.messages());
        return result;
    }

    private Map<String, Object> completion(McpJsonRpcRequest message, McpCallContext context) {
        if (completionRegistry == null) {
            throw new McpProtocolException("MCP_METHOD_NOT_FOUND", -32601,
                    "Completion is not configured", Map.of());
        }
        Map<String, Object> argument = object(message.params().get("argument"));
        String name = requiredString(argument.get("name"), "params.argument.name");
        String value = requiredString(argument.get("value"), "params.argument.value");
        Map<String, Object> reference = object(message.params().get("ref"));
        Map<String, String> contextArguments = stringMap(
                object(object(message.params().get("context")).get("arguments")));
        McpCompletionResult completion = completionRegistry.handler()
                .complete(new McpCompletionRequest(reference, name, value, contextArguments), context)
                .toCompletableFuture().join();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultType", "complete");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("values", completion.values());
        if (completion.total() != null) values.put("total", completion.total());
        if (completion.hasMore()) values.put("hasMore", true);
        payload.put("completion", values);
        return payload;
    }

    private Map<String, Object> toolsCall(McpJsonRpcRequest message, McpCallContext context) {
        String name = requiredString(message.params().get("name"), "params.name");
        Map<String, Object> arguments = object(message.params().get("arguments"));
        McpToolResponse response = dispatcher.dispatch(name, arguments, context)
                .toCompletableFuture()
                .join();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", response.resultType());
        if (!response.inputRequests().isEmpty()) {
            result.put("inputRequests", response.inputRequests());
        }
        if (response.requestState() != null) {
            result.put("requestState", response.requestState());
        }
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

    private Map<String, Object> wireResource(McpResourceDescriptor descriptor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", descriptor.uri());
        result.put("name", descriptor.name());
        if (descriptor.title() != null) result.put("title", descriptor.title());
        if (descriptor.description() != null) result.put("description", descriptor.description());
        if (descriptor.mimeType() != null) result.put("mimeType", descriptor.mimeType());
        if (descriptor.size() != null) result.put("size", descriptor.size());
        if (!descriptor.icons().isEmpty()) result.put("icons", descriptor.icons());
        if (!descriptor.annotations().isEmpty()) result.put("annotations", descriptor.annotations());
        return result;
    }

    private Map<String, Object> wireResourceTemplate(McpResourceTemplateDescriptor descriptor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uriTemplate", descriptor.uriTemplate());
        result.put("name", descriptor.name());
        if (descriptor.title() != null) result.put("title", descriptor.title());
        if (descriptor.description() != null) result.put("description", descriptor.description());
        if (descriptor.mimeType() != null) result.put("mimeType", descriptor.mimeType());
        if (!descriptor.icons().isEmpty()) result.put("icons", descriptor.icons());
        if (!descriptor.annotations().isEmpty()) result.put("annotations", descriptor.annotations());
        return result;
    }

    private Map<String, Object> wirePrompt(McpPromptDescriptor descriptor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", descriptor.name());
        if (descriptor.title() != null) result.put("title", descriptor.title());
        if (descriptor.description() != null) result.put("description", descriptor.description());
        if (!descriptor.arguments().isEmpty()) result.put("arguments", descriptor.arguments());
        return result;
    }

    private Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", Map.of("listChanged", false));
        if (!resourceRegistry.listTemplates().isEmpty() || !resourceRegistry.list(new McpCallContext(
                "discover", McpProtocolVersions.V_2026_07_28, "anonymous", "anonymous", null, Map.of(), null, null)).isEmpty()) {
            result.put("resources", Map.of("listChanged", false, "subscribe", false));
        }
        if (!promptRegistry.list().isEmpty()) {
            result.put("prompts", Map.of("listChanged", false));
        }
        if (completionRegistry != null) result.put("completions", Map.of());
        return result;
    }

    private Map<String, String> stringMap(Map<String, Object> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value instanceof String text) result.put(key, text);
        });
        return result;
    }

    private Map<String, Object> pageResult(
            String key,
            List<Map<String, Object>> values,
            Map<String, Object> params,
            Map<String, Object> cacheHints) {
        int offset = cursorCodec.decode(optionalString(params.get("cursor")));
        int pageSize = pageSize(params.get("pageSize"));
        if (offset > values.size()) {
            throw new McpProtocolException("MCP_INVALID_CURSOR", -32602,
                    "Pagination cursor is out of range", Map.of());
        }
        int end = Math.min(values.size(), offset + pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", "complete");
        result.put(key, values.subList(offset, end));
        if (end < values.size()) {
            result.put("nextCursor", cursorCodec.encode(end));
        }
        result.putAll(cacheHints);
        return result;
    }

    private int pageSize(Object value) {
        if (value == null) return 50;
        if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > 100) {
            throw new McpProtocolException("MCP_INVALID_PARAMS", -32602,
                    "pageSize must be an integer between 1 and 100", Map.of());
        }
        return number.intValue();
    }

    private String optionalString(Object value) {
        return value == null ? null : requiredString(value, "params.cursor");
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
