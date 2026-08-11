/**
 * MCP 安全-OAuth 集成包：提供 MCP 客户端与外部 OAuth 2.1 / OpenID Connect 授权服务器
 * 互通所需的发现、令牌、注册与质询能力。
 *
 * <p>本包围绕 RFC 8414（Authorization Server Metadata）、RFC 7636（PKCE）、
 * RFC 7662（Token Introspection）、RFC 7591（Dynamic Client Registration）、
 * RFC 8707（Resource Indicators）、RFC 9728（Protected Resource Metadata）以及
 * RFC 6750（Bearer Token Usage）构建，旨在让 MCP Client Starter / Resource Server
 * 无需重复实现 OAuth 协议细节即可与任意合规 AS 对接。</p>
 *
 * <p>包内主要类与职责：</p>
 *
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthTokenProvider} —
 *       令牌获取 SPI，定义客户端如何拿到适用于指定资源的 access token；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthTokenManager} —
 *       默认 SPI 实现，自持 access/refresh token 并按需自动刷新；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthTokenClient} —
 *       框架中立的 OAuth HTTP 客户端（authorization_code / refresh_token /
 *       client_credentials / introspect / register）；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthMetadataClient} —
 *       RFC 8414 / RFC 9728 metadata 发现客户端；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthPkce} —
 *       RFC 7636 PKCE S256 helper（生成 verifier / 派生 challenge / 常量时间校验）；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthUriPolicy} —
 *       SSRF 安全边界 SPI，所有出站 URI 必须经其校验；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthHeaders} —
 *       RFC 6750 §3 / RFC 9728 §5 规定的 Bearer Header 生成器；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthAuthorizationRequest} —
 *       授权码请求装配器，输出符合 OAuth 2.1 §4.1.1 的授权 URI；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpOAuthAccessToken} /
 *       {@link cn.richie696.component.mcp.security.oauth.McpOAuthTokenResponse} /
 *       {@link cn.richie696.component.mcp.security.oauth.McpOAuthIntrospectionResponse} /
 *       {@link cn.richie696.component.mcp.security.oauth.McpOAuthClientRegistration} —
 *       协议响应的内部不可变值对象；</li>
 *   <li>{@link cn.richie696.component.mcp.security.oauth.McpAuthorizationServerMetadata} /
 *       {@link cn.richie696.component.mcp.security.oauth.McpProtectedResourceMetadata} /
 *       {@link cn.richie696.component.mcp.security.oauth.McpProtectedResourceMetadataCodec} —
 *       metadata 文档的归一化模型与编码器。</li>
 * </ul>
 *
 * <p>为何把所有类放在同一个包：上述类型共同构成"OAuth 在 MCP 中的完整客户端 / 服务端
 * 运行时模型"，相互之间通过 record / interface / SPI 紧密耦合（例如
 * {@code McpOAuthTokenManager} 依赖 {@code McpOAuthTokenClient}、{@code McpOAuthAccessToken}、
 * {@code McpOAuthUriPolicy}），分到子包反而会暴露内部实现细节并制造循环依赖。
 * 包级别安全策略与共享的 RFC 引用也让单包定位成为合理选择。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.security.oauth;