package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 不可变的授权码请求装配器，强制携带 S256 PKCE。
 *
 * <p>该 record 负责把授权码流程所需的全部参数（client、redirect、scope、resource、state、PKCE
 * challenge）封装为一个值对象，并通过 {@link #toUri()} 直接产出符合 OAuth 2.1 §4.1.1 与
 * RFC 7636 的授权请求 URI，供 MCP 客户端在浏览器或外部 user-agent 跳转中使用。</p>
 *
 * <p>为什么强制 S256：MCP 客户端是 public client（无法持有密钥），PKCE 是必备防护；
 * RFC 7636 与 OAuth 2.1 都明确反对 {@code plain} 模式，因此本类不接受明文 challenge，
 * 由调用方在上游用 {@link McpOAuthPkce#challenge(String)} 提前派生。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpOAuthAuthorizationRequest(
        URI authorizationEndpoint,
        String clientId,
        URI redirectUri,
        String scope,
        String resource,
        String state,
        String codeChallenge) {

    /**
     * 紧凑构造器：对必填字段做非空校验。
     *
     * <p>{@code state} 与 {@code codeChallenge} 之所以强制非空：state 用于回调时抵御 CSRF，
     * codeChallenge 是 PKCE 校验的源头——这两者在授权码流中缺失都会导致后续 token 交换被
     * AS 拒绝或在生产环境被攻击者利用，因此在构造阶段即 fail-fast 优于运行时兜底。</p>
     *
     * @param authorizationEndpoint 授权端点（必填）
     * @param clientId 客户端标识（必填）
     * @param redirectUri 回调地址（必填，必须事先在 AS 注册）
     * @param scope 请求的授权范围，可为 null
     * @param resource 受众资源指示符（RFC 8707），可为 null
     * @param state 抗 CSRF 随机串（必填）
     * @param codeChallenge S256 派生的 challenge（必填，调用方已派生）
     * @throws NullPointerException 当 authorizationEndpoint/clientId/redirectUri 为 null 时
     * @throws IllegalArgumentException 当 state 或 codeChallenge 为空时
     */
    public McpOAuthAuthorizationRequest {
        Objects.requireNonNull(authorizationEndpoint, "authorizationEndpoint");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(redirectUri, "redirectUri");
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("codeChallenge must not be blank");
        }
    }

    /**
     * 拼接出最终的授权请求 URI（含 query 参数）。
     *
     * <p>实现要点：保留授权端点已有的 query 片段（兼容某些 AS 在 endpoint URL 中携带自定义参数），
     拼接顺序遵循 LinkedHashMap 的插入序，保证输出可被单元测试断言；统一使用
     {@link URLEncoder} + UTF-8 编码避免跨平台字符差异；scheme/authority/path 取 raw 形式
     防止 URI 重编码破坏端口号与百分号编码字符。</p>
     *
     * @return 形如 {@code https://as.example/authorize?response_type=code&client_id=...&code_challenge_method=S256} 的 URI
     * @throws IllegalArgumentException 当 authorizationEndpoint 不合法（无法解析为 URI）时
     */
    public URI toUri() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri.toString());
        if (scope != null && !scope.isBlank()) params.put("scope", scope);
        if (resource != null && !resource.isBlank()) params.put("resource", resource);
        params.put("state", state);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        try {
            URI base = new URI(authorizationEndpoint.getScheme(), authorizationEndpoint.getRawAuthority(),
                    authorizationEndpoint.getPath(), null, authorizationEndpoint.getFragment());
            return URI.create(base + (base.getRawQuery() == null ? "?" : "&") + query);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid authorization endpoint", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
