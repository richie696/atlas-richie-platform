package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Refreshing OAuth token provider suitable for wiring into the MCP Client Starter.
 *
 * <p>面向 MCP Client Starter 的自刷新令牌管理器，实现 {@link McpOAuthTokenProvider} SPI：
 * 内部以 {@link AtomicReference} 持有当前 access token 与可选的 refresh token，
 * 在 token 即将过期（基于 {@link #CLOCK_SKEW} 时钟偏差）或 scope 不满足需求时自动
 * 触发刷新流程。该类不感知具体 OAuth 协议细节，把 HTTP 调用全部委托给
 * {@link McpOAuthTokenClient}。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>双检查 + 同步块：先无锁快路径返回现有 token，仅在快路径失败时进入 {@code synchronized}
 *       临界区，避免每次请求都加锁；</li>
 *   <li>refresh_token 优先：持有 refresh token 时走 refresh_token grant，否则退化到
 *       client_credentials（MCP 服务端典型用法）；</li>
 *   <li>scope 合并：调用方声明的 required scopes 与启动期配置的 scopes 取并集，确保
 *       既有能力不会因下游调用临时注入 scope 而被覆盖丢失。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpOAuthTokenManager implements McpOAuthTokenProvider {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final McpOAuthTokenClient client;
    private final URI tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final URI resource;
    private final Set<String> configuredScopes;
    private final AtomicReference<McpOAuthAccessToken> accessToken = new AtomicReference<>();
    private final AtomicReference<String> refreshToken = new AtomicReference<>();

    /**
     * 构造自刷新令牌管理器。
     *
     * <p>约束：{@code clientId} 与 {@code resource} 必填——前者用于向 AS 标识 client，
     后者是 RFC 8707 资源指示符，与后续 {@link #tokenFor(URI, Set)} 的
     {@code requestedResource} 做精确匹配（不匹配返回 {@link Optional#empty()} 以隔离
     多租户 MCP 场景的资源串扰）。</p>
     *
     * @param client 框架中立的 token HTTP 客户端
     * @param tokenEndpoint token 端点 URI
     * @param clientId 客户端标识
     * @param clientSecret 客户端密钥（public client 可为 null）
     * @param resource 受众资源指示符（RFC 8707），用于多租户隔离
     * @param configuredScopes 启动期静态配置的 scope 集合（不可变拷贝），可为 null/空
     * @throws NullPointerException 当 client/tokenEndpoint/clientId/resource 为 null 时
     */
    public McpOAuthTokenManager(
            McpOAuthTokenClient client,
            URI tokenEndpoint,
            String clientId,
            String clientSecret,
            URI resource,
            Set<String> configuredScopes) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.tokenEndpoint = java.util.Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        this.clientId = java.util.Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = clientSecret;
        this.resource = java.util.Objects.requireNonNull(resource, "resource");
        this.configuredScopes = configuredScopes == null ? Set.of() : Set.copyOf(configuredScopes);
    }

    /**
     * 写入已获得的 token 响应（由授权码流调用方回调）。
     *
     * <p>仅当响应中 refresh_token 非空时才覆盖本地 refresh_token：部分 AS 在每次
     刷新时仅在 token rotation 策略下才返回新 refresh_token，保留旧值可避免
     "明明拿到了新 access token 却丢掉了还能用的 refresh token" 的反直觉行为。</p>
     *
     * @param response authorization_code 或 refresh 调用的响应
     * @throws NullPointerException 当 response 为 null 时
     */
    public void accept(McpOAuthTokenResponse response) {
        java.util.Objects.requireNonNull(response, "response");
        accessToken.set(response.withRefreshTokenMetadata());
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            refreshToken.set(response.refreshToken());
        }
    }

    /**
     * 获取当前有效的 access token；过期或 scope 不足时自动刷新。
     *
     * <p>实现策略：</p>
     * <ol>
     *   <li>{@code requestedResource} 与本管理器绑定的 resource 不一致 → 返回 {@code empty}
     *       以阻止跨资源 token 串用；</li>
     *   <li>快路径：当前 token 仍有效且覆盖全部 required scopes，直接返回；</li>
     *   <li>慢路径（{@code synchronized}）：再次检查避免重复刷新，按"refresh_token 优先，
     *       缺失则走 client_credentials"策略触发实际换 token。</li>
     * </ol>
     *
     * @param requestedResource 调用方声称的资源（用于多租户校验）；null 时跳过资源检查
     * @param requiredScopes 调用方声明所需的 scope 集合
     * @return 当前可用的 access token；若资源不匹配则返回 {@code empty}
     */
    @Override
    public CompletionStage<Optional<McpOAuthAccessToken>> tokenFor(
            URI requestedResource,
            Set<String> requiredScopes) {
        if (requestedResource != null && !resource.equals(requestedResource)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Set<String> required = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        McpOAuthAccessToken current = accessToken.get();
        if (usable(current, required)) {
            return CompletableFuture.completedFuture(Optional.of(current));
        }
        synchronized (this) {
            current = accessToken.get();
            if (usable(current, required)) {
                return CompletableFuture.completedFuture(Optional.of(current));
            }
            McpOAuthTokenResponse refreshed = refreshToken.get() == null
                    ? client.clientCredentials(tokenEndpoint, clientId, clientSecret, resource, requestedScopes(required))
                    : client.refreshToken(tokenEndpoint, clientId, clientSecret, refreshToken.get(), resource, requestedScopes(required));
            accept(refreshed);
            return CompletableFuture.completedFuture(Optional.of(accessToken.get()));
        }
    }

    /**
     * 判定 token 当前是否可直接复用。
     *
     * <p>同时校验三项：非空、未过期（带 30 秒时钟偏差以容忍 NTP 漂移与即将过期请求）、
     包含全部 required scopes。任一不满足即视为不可用并触发刷新。</p>
     *
     * @param token 当前缓存的 token（可为 null）
     * @param requiredScopes 调用方要求的 scopes
     * @return true 表示无需刷新即可直接复用
     */
    private boolean usable(McpOAuthAccessToken token, Set<String> requiredScopes) {
        return token != null && !token.expired(CLOCK_SKEW) && token.scopes().containsAll(requiredScopes);
    }

    /**
     * 合并启动期静态 scopes 与本次调用动态 required scopes。
     *
     * <p>使用 {@link LinkedHashSet} 保证顺序稳定（便于日志追踪与单元测试断言）；
     取并集而非覆盖，是为了避免下游"动态注入一个 scope"反而导致原有静态能力消失。
     最终再做 {@link Set#copyOf} 不可变封装，使合并结果可安全跨线程传递。</p>
     *
     * @param required 本次调用新增的 scopes
     * @return 不可变 scope 集合
     */
    private Set<String> requestedScopes(Set<String> required) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>(configuredScopes);
        scopes.addAll(required);
        return Set.copyOf(scopes);
    }
}
