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
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;
import cn.richie696.component.mcp.api.server.McpCallContextFactory;
import cn.richie696.component.mcp.api.server.McpServerCallContextRequest;
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
 * 与 Web 框架无关的现代 MCP 端点；Spring/Netty 等适配器只需把 HTTP 值映射为本类型即可使用。
 *
 * <p>这是 MCP 协议层与服务端业务层的"门面协调者"，职责限定在以下几点：
 * <ul>
 *   <li>JSON envelope 解析与下一阶段标准化。</li>
 *   <li>委托 {@link McpStreamableHttpRequestValidator} 完成协议/Header 校验。</li>
 *   <li>把方法分派到底层 registry（{@link McpToolRegistry} / {@link McpResourceRegistry} /
 *       {@link McpPromptRegistry} / {@link McpCompletionRegistry}）。</li>
 *   <li>统一错误格式、HTTP 状态码映射、SSE 通知聚合。</li>
 *   <li>维护运行时上下文（取消注册、订阅管理、call context 工厂）。</li>
 * </ul>
 *
 * <p>关键设计抉择：
 * <ul>
 *   <li>使用对外不可见字段（final）严格构造——避免后续运行时热替换带来的状态不一致。</li>
 *   <li>{@link McpCancellationRegistry} 与 {@link McpSubscriptionManager} 都是端点实例独占，
 *       保证不同 endpoint 之间不会跨边界泄露请求上下文。</li>
 *   <li>{@link #handle(String, Map)} 是单一入站入口，简化了 Spring MVC 与 Reactive 适配器
 *       在"是否单次请求"上的分歧——两端只需把框架对象归约为 (jsonBody, headers)。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
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
    private final McpCallContextFactory callContextFactory;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final McpDiscoveryCodec discoveryCodec = new McpDiscoveryCodec();

    /**
     * 最少参数构造：默认通过所有 Origin、内置空资源与提示注册器、不开启补全。
     *
     * @param registry    工具注册表
     * @param serverInfo  服务器元数据，会出现在 {@code server/discover} 响应中
     */
    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo) {
        this(registry, serverInfo, origin -> true, new McpResourceRegistry(), new McpPromptRegistry(), null);
    }

    /**
     * 指定 Origin 校验策略；其余字段默认。
     */
    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy) {
        this(registry, serverInfo, originPolicy, new McpResourceRegistry(), new McpPromptRegistry(), null);
    }

    /**
     * 完整 Registry 三件套；不启用补全。
     */
    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry) {
        this(registry, serverInfo, originPolicy, resourceRegistry, promptRegistry, null);
    }

    /**
     * 启用补全注册器；不提供工具调用拦截器与 call context 工厂。
     */
    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            McpCompletionRegistry completionRegistry) {
        this(registry, serverInfo, originPolicy, resourceRegistry, promptRegistry,
                completionRegistry, List.of(), McpCallContextFactory.anonymous());
    }

    /**
     * 提供工具调用拦截器；call context 工厂默认为匿名。
     */
    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            McpCompletionRegistry completionRegistry,
            List<McpToolInvocationInterceptor> invocationInterceptors) {
        this(registry, serverInfo, originPolicy, resourceRegistry, promptRegistry,
                completionRegistry, invocationInterceptors, McpCallContextFactory.anonymous());
    }

    /**
     * 全参数构造。
     *
     * <p>构造阶段会自动为传入的 {@link McpToolRegistry} 注册一个变更监听器，
     * 当 {@code tools/list} 中的工具增减时自动向所有订阅者广播
     * {@code notifications/tools/list_changed}。</p>
     *
     * @param registry               工具注册表
     * @param serverInfo             服务器标识信息
     * @param originPolicy           跨域 Origin 校验策略
     * @param resourceRegistry       资源注册表
     * @param promptRegistry         提示注册表
     * @param completionRegistry     补全注册器，{@code null} 表示禁用补全
     * @param invocationInterceptors 工具调用拦截器链
     * @param callContextFactory     call context 工厂，用于创建 {@link McpCallContext}
     */
    public McpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo serverInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            McpCompletionRegistry completionRegistry,
            List<McpToolInvocationInterceptor> invocationInterceptors,
            McpCallContextFactory callContextFactory) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dispatcher = new McpToolDispatcher(registry, invocationInterceptors);
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
        this.callContextFactory = Objects.requireNonNull(callContextFactory, "callContextFactory");
        registry.addChangeListener(result -> this.subscriptionManager.toolsChanged());
    }

    /**
     * 单条 HTTP 请求的统一入口；适配器只需把请求归约为 (jsonBody, headers) 即可调用本方法。
     *
     * <p>处理流程：
     * <ol>
     *   <li>组装 {@link McpHttpRequest} 并交给 {@link McpStreamableHttpRequestValidator}。</li>
     *   <li>若为 {@code notifications/cancelled} 则唤醒取消令牌并返回 202。</li>
     *   <li>若为 {@code subscriptions/listen} 则打开订阅并返回 SSE。</li>
     *   <li>其余请求：注册取消令牌 → 构造 {@link McpCallContext} → 按 method 分派 → 包装响应。
     *       在 finally 中确保取消令牌被清理防止内存泄漏。</li>
     * </ol>
     *
     * <p>异常路径：所有异常会被捕获并转译为对应的 JSON-RPC 错误响应——
     * {@link McpHttpTransportException} 透传 HTTP 状态码与 {@link McpProtocolException}；
     * {@link McpProtocolException} 根据 code 决定 404（-32601）或 400；其他异常统一 500 Internal Error。</p>
     *
     * @param jsonBody HTTP body 文本
     * @param headers  多值请求头
     * @return 框架无关的 HTTP 响应，普通请求为 JSON、SSE 场景为 text/event-stream
     */
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
            McpCallContext context = context(
                    validated, request.headers(), cancellationToken, notifications);
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

    /**
     * @return 端点持有的 {@link McpSubscriptionManager}，供上层在工具/资源变更时主动广播
     */
    public McpSubscriptionManager subscriptionManager() {
        return subscriptionManager;
    }

    /**
     * 处理 {@code subscriptions/listen} 请求：解析通知兴趣 → 调用
     * {@link McpSubscriptionManager#open(String, McpSubscriptionSpec)} → 返回 SSE 响应。
     *
     * @param message 已通过校验的 JSON-RPC 请求
     * @return SSE 长连接，包含订阅 acknowledge 通知
     * @throws McpProtocolException {@code params.notifications} 缺失时
     */
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

    /**
     * 反序列化 HTTP body 为 {@link McpJsonRpcRequest}。
     *
     * <p>解析失败时返回 {@code (jsonrpc=null, id=null, method=null, params=Map.of())}
     * 这种"几乎为空"的请求对象：让后续校验阶段抛 {@link McpProtocolException} 而不是
     * 让 Jackson 异常向上扩散。</p>
     *
     * @param body 原始 body 文本
     * @return 解析结果
     */
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

    /**
     * 把 HTTP 入站参数、取消令牌、进度令牌聚合成 {@link McpCallContext}，
     * 注入到工具/资源/提示的具体业务代码中。
     *
     * <p>进度令牌从 {@code params.progressToken} 或 {@code params._meta.progressToken}
     * 任意位置取值——前者是新版首选字段，后者兼容旧协议；提供 {@link McpProgressReporter}
     * 在工具内调用 {@code report(...)} 时就会自动把消息累积到 SSE 通知列表。</p>
     *
     * @param request            校验后的 HTTP/JSON-RPC 请求
     * @param headers            多值 HTTP 头
     * @param cancellationToken  本请求的取消令牌
     * @param notifications      SSE 通知缓冲（progress 等）
     * @return 不为 null 的业务 call context
     */
    private McpCallContext context(
            McpValidatedHttpRequest request,
            Map<String, List<String>> headers,
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
        McpCallContext context = callContextFactory.create(new McpServerCallContextRequest(
                String.valueOf(request.message().id()),
                request.protocolVersion(),
                headers,
                attributes,
                Instant.now().plusSeconds(30),
                cancellationToken,
                progressReporter));
        return Objects.requireNonNull(context, "MCP call context factory returned null");
    }

    /**
     * {@link McpProgressReporter} 的具体实现：累积到 SSE 通知列表，并校验单调性。
     *
     * <p>关键约束：{@code progress} 必须是有限的、单调递增的——这是协议规范的硬要求，
     * 不满足直接抛 {@link IllegalArgumentException}，让业务调用方立刻知道调用错误。</p>
     */
    private static final class ProgressCollector implements McpProgressReporter {
        private final Object token;
        private final List<Map<String, Object>> notifications;
        private double lastProgress = Double.NEGATIVE_INFINITY;

        private ProgressCollector(Object token, List<Map<String, Object>> notifications) {
            this.token = token;
            this.notifications = notifications;
        }

        /**
         * 记录一条进度通知，按 MCP 协议字段组装 envelope。
         *
         * <p>实现是同步的，使用同一 {@code lastProgress} 字段保证单调；
         * {@link IllegalArgumentException} 用作业务编程错误的快速信号。</p>
         *
         * @param progress 当前进度值；必须有限，且 ≥ 上一次
         * @param total    总进度（可选）
         * @param message  进度描述（可选）
         * @throws IllegalArgumentException 参数非法或进度回退
         */
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

    /**
     * 实现 {@code server/discover}：构造一个 {@link cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult}
     * 并通过 {@link McpDiscoveryCodec} 编码为 JSON-RPC result。
     *
     * <p>Capabilities 项根据当前 registry 中的内容动态判断；存在资源则声明
     * {@code resources.listChanged=false, subscribe=false}（因为 SSE 订阅已通过
     * 专用方法承接，不再单独挂到 resources 能力上）。</p>
     *
     * @return 已编码的 discover result Map
     */
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

    /**
     * {@code tools/list} 实现：从 registry 拍快照、按 pageSize/cursor 切割并附上缓存 hint。
     */
    private Map<String, Object> toolsList(Map<String, Object> params, McpCallContext context) {
        List<Map<String, Object>> tools = registry.snapshot(context).tools().stream()
                .map(this::wireTool)
                .toList();
        return pageResult("tools", tools, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    /**
     * {@code resources/list} 实现。
     */
    private Map<String, Object> resourcesList(Map<String, Object> params, McpCallContext context) {
        List<Map<String, Object>> resources = resourceRegistry.list(context).stream()
                .map(this::wireResource)
                .toList();
        return pageResult("resources", resources, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    /**
     * {@code resources/templates/list} 实现。
     */
    private Map<String, Object> resourceTemplatesList(Map<String, Object> params) {
        List<Map<String, Object>> templates = resourceRegistry.listTemplates().stream()
                .map(this::wireResourceTemplate)
                .toList();
        return pageResult("resourceTemplates", templates, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    /**
     * {@code resources/read} 实现：通过 registry 解析 URI → handler → 读资源。
     */
    private Map<String, Object> resourcesRead(McpJsonRpcRequest message, McpCallContext context) {
        String uri = requiredString(message.params().get("uri"), "params.uri");
        McpResourceRegistration registration = resourceRegistry.resolve(uri, context);
        McpResourceContent content = registration.handler().read(uri, context)
                .toCompletableFuture().join();
        return McpCacheHints.add(Map.of("resultType", "complete", "contents", content.contents()), 0,
                McpCacheScope.PRIVATE);
    }

    /**
     * {@code prompts/list} 实现。
     */
    private Map<String, Object> promptsList(Map<String, Object> params) {
        List<Map<String, Object>> prompts = promptRegistry.list().stream()
                .map(this::wirePrompt)
                .toList();
        return pageResult("prompts", prompts, params, McpCacheHints.add(Map.of(), 60_000,
                McpCacheScope.PRIVATE));
    }

    /**
     * {@code prompts/get} 实现。
     */
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

    /**
     * {@code completion/complete} 实现：仅在 {@link #completionRegistry} 非空时启用，
     * 其余场景按协议返回 method not found。
     */
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

    /**
     * {@code tools/call} 实现：把请求转给 {@link McpToolDispatcher}，按 MRTR 协议结构化输出。
     */
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

    /**
     * 把领域侧的 {@link McpToolDescriptor} 转写为 JSON-RPC {@code tools/list} 中元素的 wire 形式。
     */
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

    /**
     * 把领域侧的 {@link McpResourceDescriptor} 转写为 JSON-RPC {@code resources/list} 元素的 wire 形式。
     */
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

    /**
     * 把 {@link McpResourceTemplateDescriptor} 转写为 JSON-RPC {@code resources/templates/list} 元素的 wire 形式。
     */
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

    /**
     * 把 {@link McpPromptDescriptor} 转写为 JSON-RPC {@code prompts/list} 元素的 wire 形式。
     */
    private Map<String, Object> wirePrompt(McpPromptDescriptor descriptor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", descriptor.name());
        if (descriptor.title() != null) result.put("title", descriptor.title());
        if (descriptor.description() != null) result.put("description", descriptor.description());
        if (!descriptor.arguments().isEmpty()) result.put("arguments", descriptor.arguments());
        return result;
    }

    /**
     * 生成 server/discover 中的 capabilities 子节，根据 registry 内容动态判断需要的字段。
     */
    private Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", Map.of("listChanged", true));
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

    /**
     * 把 {@code Map<String, Object>} 中所有 String 值收窄为 {@code Map<String, String>}，
     * 跳过非 String 类型——典型的 {@code completion} {@code context.arguments} 路径。
     */
    private Map<String, String> stringMap(Map<String, Object> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value instanceof String text) result.put(key, text);
        });
        return result;
    }

    /**
     * 通用分页渲染：cursor 解码 → slice → 编码 nextCursor → 合并缓存提示。
     *
     * @param key        结果字段名（如 {@code "tools"}）
     * @param values     完整已排序值集合
     * @param params     客户端传入的 {@code cursor} 与 {@code pageSize}
     * @param cacheHints 缓存 hint Map
     * @return 含 {@code resultType/[*key]/nextCacheHint} 的分页结果
     * @throws McpProtocolException cursor 越界或 pageSize 非法
     */
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

    /**
     * 解析 + 校验客户端传入的 {@code pageSize}：仅接受 1..100 之间的整数。
     * 提供紧约束目的是同时防止小请求恶意大 pageSize 拖慢服务端。
     */
    private int pageSize(Object value) {
        if (value == null) return 50;
        if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > 100) {
            throw new McpProtocolException("MCP_INVALID_PARAMS", -32602,
                    "pageSize must be an integer between 1 and 100", Map.of());
        }
        return number.intValue();
    }

    /**
     * nullable 版本的安全字符串取值：null → null；非 null 时通过 {@link #requiredString} 严格校验。
     */
    private String optionalString(Object value) {
        return value == null ? null : requiredString(value, "params.cursor");
    }

    /**
     * 包装 JSON-RPC 正常响应。
     */
    private Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    /**
     * 包装 JSON-RPC 错误响应。
     */
    private Map<String, Object> errorResponse(Object id, Map<String, Object> error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    /**
     * 将协议异常扁平化为 JSON-RPC error 字段。
     */
    private Map<String, Object> error(McpProtocolException exception) {
        return Map.of(
                "code", exception.jsonRpcCode(),
                "message", exception.getMessage(),
                "data", exception.data());
    }

    /**
     * 安全窄化任意值为 {@code Map<String, Object>}，null 视为空 Map，类型不符同样空 Map。
     * 与 {@link McpHttpToolClient#object(Object, String)} 行为略有差异——
     * 服务端偏向宽容，反序列化失败视为空对象以便一致地继续后续逻辑。
     */
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

    /**
     * 必须为非空字符串；非 String 或空白字符串抛 {@link McpProtocolException}，
     * 由 {@link #handle(String, Map)} 路径统一翻译为 400 错误。
     */
    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new McpProtocolException("MCP_INVALID_PARAMS", -32602, field + " must be non-blank", Map.of());
        }
        return text;
    }
}
