package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.api.McpClientRequest;
import cn.richie696.component.mcp.api.McpDynamicOperations;
import cn.richie696.component.mcp.api.McpOperations;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.protocol.McpProtocolNegotiator;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.compatibility.McpProtocolEraCache;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import cn.richie696.component.mcp.security.oauth.McpOAuthAccessToken;
import cn.richie696.component.mcp.security.oauth.McpOAuthTokenProvider;
import cn.richie696.component.mcp.transport.http.McpHttpToolClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 基于配置的 MCP HTTP Client 操作门面实现。
 *
 * <p>同时实现 {@link McpOperations}（按 serverId 路由的"命名空间"语义）和 {@link McpDynamicOperations}
 * （按 {@link McpClientRequest} 携带的动态 endpoint/headers 调用的"无固定 server"语义），
 * 并通过 {@link McpClientCacheControl} 暴露缓存失效 SPI。
 * 该类统一处理：(1) 协议版本协商（按 server/endpoint 缓存到 {@link McpProtocolEraCache}）；
 * (2) list/discovery 结果缓存（{@link McpClientResultCache}）；
 * (3) OAuth 令牌注入（{@link McpOAuthTokenProvider}）；
 * (4) 异步化（统一通过 {@link CompletableFuture#supplyAsync(Supplier)}）。</p>
 *
 * <p>安全权衡：当存在 {@code tokenProvider} 时，主动禁用 list 结果缓存——token provider 可能代表不同终端用户，
 * 在没有主体指纹的情况下共享列表将构成"授权泄漏"，参见 {@link #cached} 的注释。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpHttpOperations implements McpOperations, McpDynamicOperations, McpClientCacheControl {
    private final McpHttpToolClient client;
    private final McpClientProperties properties;
    private final McpProtocolEraCache protocolEraCache;
    private final McpClientResultCache resultCache;
    private final McpOAuthTokenProvider tokenProvider;

    /**
     * 便捷构造：使用默认协议 Era 缓存与结果缓存，不启用 OAuth。
     *
     * @param client     HTTP 工具客户端
     * @param properties MCP 客户端配置
     */
    public McpHttpOperations(McpHttpToolClient client, McpClientProperties properties) {
        this(client, properties, new McpProtocolEraCache(), new McpClientResultCache());
    }

    /**
     * 自定义缓存、不启用 OAuth 的构造。
     *
     * @param client          HTTP 工具客户端
     * @param properties      MCP 客户端配置
     * @param protocolEraCache 协议 Era 缓存
     * @param resultCache     结果缓存
     */
    public McpHttpOperations(
            McpHttpToolClient client,
            McpClientProperties properties,
            McpProtocolEraCache protocolEraCache,
            McpClientResultCache resultCache) {
        this(client, properties, protocolEraCache, resultCache, null);
    }

    /**
     * 全量构造。
     *
     * @param client          HTTP 工具客户端
     * @param properties      MCP 客户端配置
     * @param protocolEraCache 协议 Era 缓存
     * @param resultCache     结果缓存
     * @param tokenProvider   可选 OAuth Token Provider；为 {@code null} 时视为未启用 OAuth
     */
    public McpHttpOperations(
            McpHttpToolClient client,
            McpClientProperties properties,
            McpProtocolEraCache protocolEraCache,
            McpClientResultCache resultCache,
            McpOAuthTokenProvider tokenProvider) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.protocolEraCache = Objects.requireNonNull(protocolEraCache, "protocolEraCache");
        this.resultCache = Objects.requireNonNull(resultCache, "resultCache");
        this.tokenProvider = tokenProvider;
    }

    /**
     * 列出指定 server 暴露的 tools，结果会被进程内缓存（不存在 tokenProvider 时）。
     *
     * @param serverId 配置中的 server ID
     * @return 异步工具描述列表
     */
    @Override
    public CompletionStage<List<McpToolDescriptor>> listTools(String serverId) {
        return async(() -> cached(serverId, "tools/list", () -> clientFor(serverId)
                .listTools(endpoint(serverId), headers(serverId))).stream()
                .map(tool -> new McpToolDescriptor(
                        tool.name(),
                        tool.title(),
                        tool.description(),
                        tool.inputSchema(),
                        tool.outputSchema(),
                        tool.annotations()))
                .toList());
    }

    /**
     * 调用指定 server 上的 tool（写操作，不走缓存）。
     *
     * @param serverId  server ID
     * @param toolName  工具名
     * @param arguments 业务参数
     * @return 异步工具调用响应
     */
    @Override
    public CompletionStage<McpToolResponse> callTool(
            String serverId,
            String toolName,
            Map<String, Object> arguments) {
        return async(() -> clientFor(serverId).callTool(
                endpoint(serverId), toolName, arguments, headers(serverId)));
    }

    /**
     * 列出 server 暴露的 resources，结果会被缓存（不存在 tokenProvider 时）。
     *
     * @param serverId server ID
     * @return 异步资源描述列表
     */
    @Override
    public CompletionStage<List<McpResourceDescriptor>> listResources(String serverId) {
        return async(() -> cached(serverId, "resources/list", () -> clientFor(serverId)
                .listResources(endpoint(serverId), headers(serverId))));
    }

    /**
     * 列出 server 暴露的 resource templates，结果会被缓存（不存在 tokenProvider 时）。
     *
     * @param serverId server ID
     * @return 异步资源模板列表
     */
    @Override
    public CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(String serverId) {
        return async(() -> cached(serverId, "resources/templates/list", () -> clientFor(serverId)
                .listResourceTemplates(endpoint(serverId), headers(serverId))));
    }

    /**
     * 读取指定 server 上的 resource（写操作，不走缓存）。
     *
     * @param serverId server ID
     * @param uri      resource URI
     * @return 异步资源内容
     */
    @Override
    public CompletionStage<McpResourceContent> readResource(String serverId, String uri) {
        return async(() -> clientFor(serverId).readResource(endpoint(serverId), uri, headers(serverId)));
    }

    /**
     * 列出 server 暴露的 prompts，结果会被缓存（不存在 tokenProvider 时）。
     *
     * @param serverId server ID
     * @return 异步 prompt 描述列表
     */
    @Override
    public CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId) {
        return async(() -> cached(serverId, "prompts/list", () -> clientFor(serverId)
                .listPrompts(endpoint(serverId), headers(serverId))));
    }

    /**
     * 获取指定 prompt 的内容（写操作，不走缓存）。
     *
     * @param serverId  server ID
     * @param name      prompt 名
     * @param arguments 模板参数
     * @return 异步 prompt 内容
     */
    @Override
    public CompletionStage<McpPromptContent> getPrompt(
            String serverId,
            String name,
            Map<String, Object> arguments) {
        return async(() -> clientFor(serverId).getPrompt(
                endpoint(serverId), name, arguments, headers(serverId)));
    }

    /**
     * 向 server 发起补全请求（写操作，不走缓存）。
     *
     * @param serverId          server ID
     * @param reference         待补全的引用（resource/prompt 等）
     * @param argumentName      参数名
     * @param value             当前已输入值
     * @param contextArguments  上下文参数
     * @return 异步补全结果
     */
    @Override
    public CompletionStage<McpCompletionResult> complete(
            String serverId,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> contextArguments) {
        return async(() -> clientFor(serverId).complete(
                endpoint(serverId), reference, argumentName, value, headers(serverId), contextArguments));
    }

    /**
     * Dynamic endpoint variant. Result caching is deliberately disabled here:
     * request headers may carry a user or service credential, so a shared list
     * cache would be unsafe without a caller-provided principal fingerprint.
     */
    /**
     * 动态 endpoint 形式的 listTools。
     *
     * <p>故意禁用结果缓存：请求头可能携带用户/服务凭证，没有"主体指纹"时共享列表会构成授权泄漏。</p>
     *
     * @param request 携带 endpoint/headers 的动态请求
     * @return 异步工具描述列表
     */
    @Override
    public CompletionStage<List<McpToolDescriptor>> listTools(McpClientRequest request) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request)
                .listTools(request.endpoint(), request.headers())
                .stream()
                .map(tool -> new McpToolDescriptor(
                        tool.name(),
                        tool.title(),
                        tool.description(),
                        tool.inputSchema(),
                        tool.outputSchema(),
                        tool.annotations()))
                .toList());
    }

    /**
     * 动态 endpoint 形式的 callTool。
     *
     * @param request   动态请求
     * @param toolName  工具名
     * @param arguments 业务参数
     * @return 异步工具调用响应
     */
    @Override
    public CompletionStage<McpToolResponse> callTool(
            McpClientRequest request,
            String toolName,
            Map<String, Object> arguments) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request).callTool(
                request.endpoint(), toolName, arguments, request.headers()));
    }

    /**
     * 动态 endpoint 形式的 listResources。
     *
     * @param request 动态请求
     * @return 异步资源描述列表
     */
    @Override
    public CompletionStage<List<McpResourceDescriptor>> listResources(McpClientRequest request) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request).listResources(request.endpoint(), request.headers()));
    }

    /**
     * 动态 endpoint 形式的 listResourceTemplates。
     *
     * @param request 动态请求
     * @return 异步资源模板列表
     */
    @Override
    public CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(
            McpClientRequest request) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request)
                .listResourceTemplates(request.endpoint(), request.headers()));
    }

    /**
     * 动态 endpoint 形式的 readResource。
     *
     * @param request 动态请求
     * @param uri     resource URI
     * @return 异步资源内容
     */
    @Override
    public CompletionStage<McpResourceContent> readResource(McpClientRequest request, String uri) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request).readResource(request.endpoint(), uri, request.headers()));
    }

    /**
     * 动态 endpoint 形式的 listPrompts。
     *
     * @param request 动态请求
     * @return 异步 prompt 描述列表
     */
    @Override
    public CompletionStage<List<McpPromptDescriptor>> listPrompts(McpClientRequest request) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request).listPrompts(request.endpoint(), request.headers()));
    }

    /**
     * 动态 endpoint 形式的 getPrompt。
     *
     * @param request   动态请求
     * @param name      prompt 名
     * @param arguments 模板参数
     * @return 异步 prompt 内容
     */
    @Override
    public CompletionStage<McpPromptContent> getPrompt(
            McpClientRequest request,
            String name,
            Map<String, Object> arguments) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request)
                .getPrompt(request.endpoint(), name, arguments, request.headers()));
    }

    /**
     * 动态 endpoint 形式的 complete。
     *
     * @param request          动态请求
     * @param reference         待补全的引用
     * @param argumentName      参数名
     * @param value             当前已输入值
     * @param contextArguments  上下文参数
     * @return 异步补全结果
     */
    @Override
    public CompletionStage<McpCompletionResult> complete(
            McpClientRequest request,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> contextArguments) {
        Objects.requireNonNull(request, "request");
        return async(() -> clientFor(request).complete(
                request.endpoint(), reference, argumentName, value, request.headers(), contextArguments));
    }

    /**
     * 失效指定 server 的全部缓存（结果缓存 + 协议 Era 缓存），由 MCP list_changed/resource_updated 通知触发。
     *
     * @param serverId server ID
     */
    @Override
    public void invalidateServerCache(String serverId) {
        resultCache.invalidateServer(serverId);
        protocolEraCache.invalidate(endpoint(serverId).toString());
    }

    /**
     * 清空全部缓存（结果缓存 + 协议 Era 缓存）。
     */
    @Override
    public void clearCaches() {
        resultCache.clear();
        protocolEraCache.clear();
    }

    /**
     * 取得"按 serverId 解析的"协议版本化 HTTP 客户端：若协商禁用则直接返回共享 client，否则命中或协商。
     *
     * @param serverId server ID
     * @return 与目标 server 协议版本对齐的客户端
     */
    private McpHttpToolClient clientFor(String serverId) {
        if (!properties.isNegotiateProtocol()) {
            return client;
        }
        URI endpoint = endpoint(serverId);
        String cacheKey = endpoint.toString();
        return protocolEraCache.get(cacheKey)
                .map(entry -> client.forProtocolVersion(entry.version()))
                .orElseGet(() -> negotiate(serverId, endpoint, cacheKey));
    }

    /**
     * 取得"按动态请求解析的"协议版本化 HTTP 客户端：使用 {@link McpClientRequest#endpointCacheKey()} 作为缓存 key。
     *
     * @param request 动态请求
     * @return 与目标 endpoint 协议版本对齐的客户端
     */
    private McpHttpToolClient clientFor(McpClientRequest request) {
        if (!properties.isNegotiateProtocol()) {
            return client;
        }
        URI endpoint = request.endpoint();
        String cacheKey = request.endpointCacheKey();
        return protocolEraCache.get(cacheKey)
                .map(entry -> client.forProtocolVersion(entry.version()))
                .orElseGet(() -> negotiate(endpoint, request.headers(), cacheKey));
    }

    /**
     * serverId 维度的协议协商入口。
     *
     * @param serverId server ID
     * @param endpoint server 端点
     * @param cacheKey 协议 Era 缓存 key
     * @return 协商完成后的协议版本化客户端
     */
    private McpHttpToolClient negotiate(String serverId, URI endpoint, String cacheKey) {
        return negotiate(endpoint, headers(serverId), cacheKey);
    }

    /**
     * 实际执行协议协商：用最新版本探测服务端能力，按"用户偏好 + 全量 SUPPORTED 列表"取交集，
     * 并用 {@code min(remoteTtl, configuredTtl)} 作为本地缓存有效期，避免一个无限大的远端 TTL 抹除本地治理。
     *
     * @param endpoint       远端 endpoint
     * @param requestHeaders 探测请求头
     * @param cacheKey       协议 Era 缓存 key
     * @return 协商完成后的协议版本化客户端
     */
    private McpHttpToolClient negotiate(
            URI endpoint,
            Map<String, String> requestHeaders,
            String cacheKey) {
        McpHttpToolClient probeClient = client.forProtocolVersion(McpProtocolVersions.V_2026_07_28);
        McpDiscoverResult discovery = probeClient.discover(endpoint, requestHeaders);
        java.util.List<String> preferences = new java.util.ArrayList<>();
        preferences.add(properties.getPreferredProtocolVersion());
        McpProtocolVersions.SUPPORTED.stream()
                .filter(version -> !preferences.contains(version))
                .forEach(preferences::add);
        String selected = new McpProtocolNegotiator(preferences)
                .negotiate(discovery.supportedVersions());
        java.time.Duration remoteTtl = discovery.ttlMs() > 0
                ? java.time.Duration.ofMillis(discovery.ttlMs())
                : properties.getNegotiationTtl();
        java.time.Duration ttl = remoteTtl.compareTo(properties.getNegotiationTtl()) < 0
                ? remoteTtl
                : properties.getNegotiationTtl();
        protocolEraCache.put(cacheKey, selected, ttl);
        return client.forProtocolVersion(selected);
    }

    /**
     * 包装结果缓存的查询/加载动作。
     *
     * <p>当存在 {@code tokenProvider} 时跳过缓存——token provider 可能代表不同终端用户，
     * 没有"主体指纹"时共享列表将构成授权泄漏。这是 MCP 客户端的"按用户隔离"安全约束，
     * 与远端 MCP 服务的"按 token 鉴权"语义对齐。</p>
     *
     * @param <T>       结果类型
     * @param serverId  server ID
     * @param operation 业务方法名（用于复合缓存 key）
     * @param loader    实际加载函数
     * @return 已缓存或刚加载的结果
     */
    private <T> T cached(String serverId, String operation, Supplier<T> loader) {
        // A token provider may represent different end users; without a principal
        // fingerprint, sharing a list result would be an authorization leak.
        if (!properties.isResultCacheEnabled() || tokenProvider != null) {
            return loader.get();
        }
        return resultCache.getOrLoad(serverId + "|" + operation, properties.getResultCacheTtl(), loader);
    }

    /**
     * 解析 serverId 对应的端点 URL。
     *
     * @param serverId server ID
     * @return 解析后的 URI
     * @throws IllegalArgumentException 当 serverId 未知或端点 URL 非法时抛出
     */
    private URI endpoint(String serverId) {
        McpClientProperties.Server server = server(serverId);
        try {
            return URI.create(server.getEndpoint());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid MCP endpoint for server: " + serverId, exception);
        }
    }

    /**
     * 构造 serverId 对应的请求头：基础 headers +（可选）OAuth Authorization 头。
     *
     * <p>若 tokenProvider 存在，则按 RFC 8707 resource 解析（缺省回退到 endpoint），并阻塞式 join token 异步任务，
     * 拿到 {@link McpOAuthAccessToken#authorizationHeader()} 后再塞入 headers。</p>
     *
     * @param serverId server ID
     * @return 不可变 headers Map
     */
    private Map<String, String> headers(String serverId) {
        McpClientProperties.Server server = server(serverId);
        Map<String, String> headers = new java.util.LinkedHashMap<>(server.getHeaders());
        if (tokenProvider == null) {
            return Map.copyOf(headers);
        }
        URI resource = server.getResource() == null
                ? endpoint(serverId)
                : URI.create(server.getResource());
        tokenProvider.tokenFor(resource, java.util.Set.copyOf(server.getScopes()))
                .toCompletableFuture()
                .join()
                .map(McpOAuthAccessToken::authorizationHeader)
                .ifPresent(value -> headers.put("Authorization", value));
        return Map.copyOf(headers);
    }

    /**
     * 解析 serverId 到对应的 {@link McpClientProperties.Server} 配置。
     *
     * @param serverId server ID
     * @return 不可变 Server 配置
     * @throws IllegalArgumentException 当 serverId 缺失或未在配置中注册时抛出
     */
    private McpClientProperties.Server server(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("MCP serverId must not be blank");
        }
        McpClientProperties.Server server = properties.getServers().get(serverId);
        if (server == null) {
            throw new IllegalArgumentException("Unknown MCP server: " + serverId);
        }
        return server;
    }

    /**
     * 异步化统一入口：把同步动作包成 {@link CompletableFuture}。
     *
     * <p>注意：当前实现直接委托给 {@link CompletableFuture#supplyAsync(Supplier)}，使用默认 ForkJoinPool。
     * 上游若需虚拟线程/Spring TaskExecutor，可在 fork 时改写此方法而不影响调用方。</p>
     *
     * @param <T>    结果类型
     * @param action 待异步化的同步动作
     * @return 异步完成阶段
     */
    private <T> CompletionStage<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action);
    }
}
