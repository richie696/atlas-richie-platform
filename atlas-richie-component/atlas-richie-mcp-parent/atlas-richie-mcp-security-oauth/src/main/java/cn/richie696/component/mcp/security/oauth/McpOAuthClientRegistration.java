package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * RFC 7591 Dynamic Client Registration 响应的内部模型。
 *
 * <p>该 record 既承载"动态注册成功"的核心字段（clientId/secret/redirect/grant/scope），又携带
 * 后续对注册对象进行写更新或删除所需的句柄（{@code registrationClientUri} +
 * {@code registrationAccessToken}）。设计要点：</p>
 *
 * <ul>
 *   <li>区分"客户端凭证"与"注册管理凭证"——后者仅用于调用 Registration API，前者用于 token 端点；</li>
 *   <li>集合字段在紧凑构造器内做不可变拷贝，使注册结果可安全跨线程缓存（典型场景：
 *       {@link McpOAuthTokenManager} 在 refresh 路径上反复访问）；</li>
 *   <li>保留 {@code registrationClientUri} + {@code registrationAccessToken} 是为了让上层实现
 *       支持 RFC 7591 §4 的 update/delete 流程，而不只是只读使用。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpOAuthClientRegistration(
        String clientId,
        String clientSecret,
        String tokenEndpointAuthMethod,
        List<URI> redirectUris,
        Set<String> grantTypes,
        Set<String> scopes,
        URI registrationClientUri,
        String registrationAccessToken) {

    /**
     * 便捷构造器：仅提供动态注册必填的核心字段。
     *
     * <p>为何暴露该重载：多数 client 流程在拿到 DCR 响应后只关心"如何换 token"，并不会立刻调用
     * Registration Management API；省略两个可选字段能让调用方代码更聚焦，避免在不需要管理端点时
     * 仍要传 {@code null, null} 这种噪声参数。</p>
     *
     * @param clientId 注册后分配的 client 标识
     * @param clientSecret 注册后分配的 secret（public client 可为 null）
     * @param tokenEndpointAuthMethod token 端点认证方式（如 {@code client_secret_basic}）
     * @param redirectUris 已注册的回调 URI 列表
     * @param grantTypes 支持的授权类型
     * @param scopes 授权范围
     */
    public McpOAuthClientRegistration(
            String clientId,
            String clientSecret,
            String tokenEndpointAuthMethod,
            List<URI> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes) {
        this(clientId, clientSecret, tokenEndpointAuthMethod, redirectUris, grantTypes, scopes, null, null);
    }

    /**
     * 紧凑构造器：校验 clientId 非空，并对集合字段做不可变拷贝。
     *
     * <p>{@code clientId} 之所以硬性必填：后续所有 OAuth 流程（authorization、token、
     * introspection）都需要 client_id 作为必传参数；若注册响应缺失该字段说明 AS 行为异常，
     * 不应在应用层静默 fallback，而是在构造阶段即暴露错误。</p>
     *
     * @param clientId 客户端标识（必填，非空）
     * @param clientSecret 客户端密钥（public client 可为 null）
     * @param tokenEndpointAuthMethod token 端点认证方法
     * @param redirectUris 回调 URI 列表（不可变拷贝）
     * @param grantTypes 支持的授权类型（不可变拷贝）
     * @param scopes 授权范围（不可变拷贝）
     * @param registrationClientUri 注册管理端点 URI，可为 null
     * @param registrationAccessToken 注册管理访问令牌，可为 null
     * @throws IllegalArgumentException 当 clientId 为 null 或 blank 时
     */
    public McpOAuthClientRegistration {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        grantTypes = grantTypes == null ? Set.of() : Set.copyOf(grantTypes);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
