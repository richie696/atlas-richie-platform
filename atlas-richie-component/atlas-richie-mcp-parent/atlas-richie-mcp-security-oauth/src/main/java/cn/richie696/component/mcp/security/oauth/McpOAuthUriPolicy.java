package cn.richie696.component.mcp.security.oauth;

import java.net.URI;

/**
 * OAuth metadata URL 的 SSRF 安全边界 SPI。
 *
 * <p>这是 MCP OAuth 模块所有 HTTP 客户端（{@link McpOAuthMetadataClient}、
 * {@link McpOAuthTokenClient}）共享的 URI 校验切面。任何即将被请求的 metadata / token
 * endpoint / registration endpoint URL 在发送前都必须经过本接口的校验，避免：</p>
 *
 * <ul>
 *   <li>SSRF（指向内网、localhost、metadata IP）；</li>
 *   <li>凭证泄露（URI 中嵌入 userInfo）；</li>
 *   <li>解析歧义（fragment 不应出现在端点 URL 中）。</li>
 * </ul>
 *
 * <p>暴露为 {@link FunctionalInterface} 是为了让自定义策略（allowlist、私有 CA 等）
 * 以单方法 lambda 形式注入；默认实现 {@link #httpsOnly()} 满足公网部署场景。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpOAuthUriPolicy {
    /**
     * 校验 URI 是否可安全请求。
     *
     * <p>实现要求：</p>
     * <ul>
     *   <li>URI 为 null 应抛 {@link IllegalArgumentException}；</li>
     *   <li>不合法 scheme / host 应抛 {@link IllegalArgumentException}；</li>
     *   <li>校验失败时不要抛出受检异常，统一以 {@link IllegalArgumentException} 表达，
     *       便于上层在 finally 块或 reactive 链中统一捕获。</li>
     * </ul>
     *
     * @param uri 待校验的 URI
     * @throws IllegalArgumentException 当 URI 不满足策略要求时
     */
    void validate(URI uri);

    /**
     * 默认 HTTPS-only 策略：仅允许 {@code https} 方案，且不允许 userInfo / fragment。
     *
     * <p>这是 MCP 在公网场景的最低门槛：metadata / token endpoint 一旦走 HTTP 即面临
     凭证窃听风险；URI 携带 userInfo（{@code https://user:pass@host/...}）会把凭据
     写入请求日志，fragment（{@code #...}）是客户端定位符而非服务端语义，都应在
     metadata 解析阶段被拒。</p>
     *
     * @return 默认 HTTPS-only URI 策略实例
     */
    static McpOAuthUriPolicy httpsOnly() {
        return uri -> {
            if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("MCP OAuth metadata URI must use HTTPS");
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("MCP OAuth metadata URI must not contain credentials or fragment");
            }
        };
    }
}
