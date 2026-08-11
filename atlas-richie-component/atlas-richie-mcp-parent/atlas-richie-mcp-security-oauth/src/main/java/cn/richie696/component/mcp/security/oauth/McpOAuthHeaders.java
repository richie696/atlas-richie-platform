package cn.richie696.component.mcp.security.oauth;

import java.util.List;
import java.util.Objects;

/**
 * MCP OAuth 标准 HTTP Header 生成器。
 *
 * <p>负责产出 MCP 传输层所需的两种 Header 字段：成功路径上的 {@code Authorization: Bearer ...}
 * 与失败路径上 RFC 6750 §3 / RFC 9728 §5 规定的 {@code WWW-Authenticate: Bearer ...} 质询。
 * 集中在这里的好处是：调用方只需关心"我有 token"或"我要返回 401"，不用重复实现 header
 * 字符串拼接与字符转义。</p>
 *
 * <p>为何不可实例化：纯静态工具类，所有方法无状态；私有构造器阻止意外继承与反射实例化。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpOAuthHeaders {
    private McpOAuthHeaders() {
    }

    /**
     * 把 access token 序列化为 {@code Authorization} 头的值（{@code <type> <value>}）。
     *
     * <p>委托给 {@link McpOAuthAccessToken#authorizationHeader()} 是为了保证 tokenType 与 value
     * 的拼接规则只有一处实现，避免在多个调用点产生不一致的输出（历史上容易出现的"漏空格"或
     * "大小写错误"都属于此类问题）。</p>
     *
     * @param token 已加载的访问令牌（必填）
     * @return 形如 {@code "Bearer eyJhbGc..."} 的头值
     * @throws NullPointerException 当 token 为 null 时
     */
    public static String bearer(McpOAuthAccessToken token) {
        return Objects.requireNonNull(token, "token").authorizationHeader();
    }

    /**
     * 生成 RFC 6750/RFC 9728 规定的 401 质询头值。
     *
     * <p>输出格式：{@code Bearer} + 可选 {@code resource_metadata="<uri>"} + 可选 {@code scope="<s1 s2>"}。
     resource_metadata 字段是 RFC 9728 §5.1 的强制项，让 client 在收到 401 后能直接
     获取授权服务器列表；scope 字段提示 client 重新发起授权时应请求的 scope 集合。</p>
     *
     * @param resourceMetadataUri 受保护资源元数据 URI（指向 RFC 9728 文档），可为空
     * @param scopes 推荐 client 请求的 scope 列表，可为空；非空时按空格拼接
     * @return 完整的 {@code WWW-Authenticate} 头值；即使所有参数为空也至少包含 {@code Bearer}
     */
    public static String unauthorizedChallenge(String resourceMetadataUri, List<String> scopes) {
        StringBuilder value = new StringBuilder("Bearer");
        if (resourceMetadataUri != null && !resourceMetadataUri.isBlank()) {
            value.append(" resource_metadata=\"").append(escape(resourceMetadataUri)).append('"');
        }
        if (scopes != null && !scopes.isEmpty()) {
            value.append(" scope=\"").append(escape(String.join(" ", scopes))).append('"');
        }
        return value.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
