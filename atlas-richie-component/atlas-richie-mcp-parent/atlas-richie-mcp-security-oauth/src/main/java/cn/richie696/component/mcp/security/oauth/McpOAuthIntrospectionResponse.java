package cn.richie696.component.mcp.security.oauth;

import java.time.Instant;
import java.util.Set;

/**
 * RFC 7662 Token Introspection 响应的内部模型。
 *
 * <p>introspection 用于在 MCP 资源服务器侧校验 opaque token 的有效性：当 access token 不是
 * 自包含 JWT 时，必须依赖 AS 实时查询才能判断其活性与归属。该 record 把 RFC 7662 的关键字段
 * 收敛为 8 个 record component，只暴露 MCP 授权决策真正需要的子集（active、scope、resource、
 * expiresAt、subject、clientId 等），其余字段（如 {@code nbf}、{@code aud} 数组等）被有意忽略，
 * 调用方若有需要可走扩展路径。</p>
 *
 * <p>为什么 {@code active} 是首字段：这是 RFC 7662 §2.2 唯一强制字段，且在资源服务器侧
 * 访问控制路径上是第一道判断（{@code !active} 直接拒绝，无需再读后续字段），放最前更符合
 * "hot field first" 的 record 布局。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpOAuthIntrospectionResponse(
        boolean active,
        String clientId,
        String subject,
        String tokenType,
        Instant expiresAt,
        Set<String> scopes,
        String issuer,
        String resource) {

    /**
     * 紧凑构造器：对 scopes 做不可变拷贝。
     *
     * <p>不可变拷贝的意义：introspection 结果可能在 MCP 请求热路径上被多个拦截器共享使用
     *（认证、审计、配额），任何一处可变 mutation 都会破坏其他消费者的语义；同时
     * {@code Set.copyOf} 会拒绝包含 null 的集合，恰好与 RFC 7662 中 scope 元素必须为非空
     * 字符串的要求对齐。</p>
     *
     * @param active 是否仍处于激活状态（RFC 7662 强制字段）
     * @param clientId token 所属客户端
     * @param subject 资源所有者/终端用户标识
     * @param tokenType 令牌类型，通常为 {@code Bearer}
     * @param expiresAt 过期时间
     * @param scopes 已授权范围集合（不可变拷贝）
     * @param issuer 颁发方
     * @param resource 受众资源指示符（RFC 8707）
     */
    public McpOAuthIntrospectionResponse {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
