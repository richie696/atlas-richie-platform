package cn.richie696.component.mcp.client.spring.boot;

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
 * 基于配置的 MCP HTTP Client 操作实现。
 */
public final class McpHttpOperations implements McpOperations, McpClientCacheControl {
    private final McpHttpToolClient client;
    private final McpClientProperties properties;
    private final McpProtocolEraCache protocolEraCache;
    private final McpClientResultCache resultCache;
    private final McpOAuthTokenProvider tokenProvider;

    public McpHttpOperations(McpHttpToolClient client, McpClientProperties properties) {
        this(client, properties, new McpProtocolEraCache(), new McpClientResultCache());
    }

    public McpHttpOperations(
            McpHttpToolClient client,
            McpClientProperties properties,
            McpProtocolEraCache protocolEraCache,
            McpClientResultCache resultCache) {
        this(client, properties, protocolEraCache, resultCache, null);
    }

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

    @Override
    public CompletionStage<McpToolResponse> callTool(
            String serverId,
            String toolName,
            Map<String, Object> arguments) {
        return async(() -> clientFor(serverId).callTool(
                endpoint(serverId), toolName, arguments, headers(serverId)));
    }

    @Override
    public CompletionStage<List<McpResourceDescriptor>> listResources(String serverId) {
        return async(() -> cached(serverId, "resources/list", () -> clientFor(serverId)
                .listResources(endpoint(serverId), headers(serverId))));
    }

    @Override
    public CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(String serverId) {
        return async(() -> cached(serverId, "resources/templates/list", () -> clientFor(serverId)
                .listResourceTemplates(endpoint(serverId), headers(serverId))));
    }

    @Override
    public CompletionStage<McpResourceContent> readResource(String serverId, String uri) {
        return async(() -> clientFor(serverId).readResource(endpoint(serverId), uri, headers(serverId)));
    }

    @Override
    public CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId) {
        return async(() -> cached(serverId, "prompts/list", () -> clientFor(serverId)
                .listPrompts(endpoint(serverId), headers(serverId))));
    }

    @Override
    public CompletionStage<McpPromptContent> getPrompt(
            String serverId,
            String name,
            Map<String, Object> arguments) {
        return async(() -> clientFor(serverId).getPrompt(
                endpoint(serverId), name, arguments, headers(serverId)));
    }

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

    /** Invalidates list caches after a server list_changed/resource_updated notification. */
    @Override
    public void invalidateServerCache(String serverId) {
        resultCache.invalidateServer(serverId);
        protocolEraCache.invalidate(endpoint(serverId).toString());
    }

    @Override
    public void clearCaches() {
        resultCache.clear();
        protocolEraCache.clear();
    }

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

    private McpHttpToolClient negotiate(String serverId, URI endpoint, String cacheKey) {
        McpHttpToolClient probeClient = client.forProtocolVersion(McpProtocolVersions.V_2026_07_28);
        McpDiscoverResult discovery = probeClient.discover(endpoint, headers(serverId));
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

    private <T> T cached(String serverId, String operation, Supplier<T> loader) {
        // A token provider may represent different end users; without a principal
        // fingerprint, sharing a list result would be an authorization leak.
        if (!properties.isResultCacheEnabled() || tokenProvider != null) {
            return loader.get();
        }
        return resultCache.getOrLoad(serverId + "|" + operation, properties.getResultCacheTtl(), loader);
    }

    private URI endpoint(String serverId) {
        McpClientProperties.Server server = server(serverId);
        try {
            return URI.create(server.getEndpoint());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid MCP endpoint for server: " + serverId, exception);
        }
    }

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

    private <T> CompletionStage<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action);
    }
}
