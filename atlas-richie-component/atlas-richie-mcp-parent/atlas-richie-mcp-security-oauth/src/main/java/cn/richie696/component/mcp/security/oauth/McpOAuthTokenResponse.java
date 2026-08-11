package cn.richie696.component.mcp.security.oauth;

import java.time.Instant;
import java.util.Set;

/**
 * RFC 6749 token 端点响应的内部模型。
 *
 * <p>封装 token endpoint 单次返回的最小信息集：当前 access token、可选 refresh token、
 * 以及响应级 scope 集合（注意它与 {@link McpOAuthAccessToken#scopes()} 不一定相同——
 * AS 在 refresh 或 scope 降级时会返回比 access token 内部 scope 更窄的集合）。
 * 该 record 主要服务于 {@link McpOAuthTokenManager#accept(McpOAuthTokenResponse)} 的
 * 回写路径，以及授权码流闭环后将响应转交给 manager 缓存。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpOAuthTokenResponse(
        McpOAuthAccessToken accessToken,
        String refreshToken,
        Set<String> scopes) {

    /**
     * 紧凑构造器：对 accessToken 做强非空约束，对 scopes 做不可变拷贝。
     *
     * <p>accessToken 必填：整个响应的核心就是 access token；缺失时说明 AS 行为异常
     （RFC 6749 §5.1 强制要求返回 access_token），应让上游立即感知。scopes 不可变
     拷贝避免后续 mutation 破坏 manager 中的缓存一致性。</p>
     *
     * @param accessToken 已获得的访问令牌（必填）
     * @param refreshToken refresh token（AS 可选返回；public client 永为 null）
     * @param scopes 响应级 scope 集合（不可变拷贝）
     * @throws NullPointerException 当 accessToken 为 null 时
     */
    public McpOAuthTokenResponse {
        java.util.Objects.requireNonNull(accessToken, "accessToken");
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    /**
     * 把响应级 scopes 同步到 access token 上，返回新的不可变 access token。
     *
     * <p>为什么需要这一步：{@link McpOAuthAccessToken} 自身已携带 scopes 字段，
     但部分 AS（如 Keycloak）在 token 响应中省略 {@code scope} 字段，导致
     {@code McpOAuthTokenClient#token()} 把 scopes 留空。{@code withRefreshTokenMetadata()}
     在 manager 回写时强制以响应级 scopes 覆盖 access token 内部 scopes，
     避免后续 {@code usable()} 判定因 scopes 缺失而误判为"scope 不满足"触发无效刷新。</p>
     *
     * @return 携带响应级 scopes 的新 access token 实例
     */
    public McpOAuthAccessToken withRefreshTokenMetadata() {
        return new McpOAuthAccessToken(
                accessToken.value(), accessToken.tokenType(), accessToken.expiresAt(),
                accessToken.issuer(), accessToken.resource(), scopes);
    }
}
