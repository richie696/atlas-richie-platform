package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.security.oauth.McpProtectedResourceMetadata;
import cn.richie696.component.mcp.security.oauth.McpProtectedResourceMetadataCodec;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * 按照 <a href="https://datatracker.ietf.org/doc/html/rfc9728">RFC 9728</a> 暴露
 * OAuth 2.0 Protected Resource Metadata，供 MCP 客户端在握手阶段发现授权服务器与受支持 scope。
 *
 * <p>仅在 {@code platform.component.mcp.server.oauth.enabled=true} 且配置了合法 {@code resource}
 * URI 时由自动配置注册；注册路径默认遵循 OAuth 规范的 {@code /.well-known/oauth-protected-resource}。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@RestController
@RequestMapping("${platform.component.mcp.server.oauth.metadata-path:/.well-known/oauth-protected-resource}")
public final class McpOAuthMetadataController {
    private final Map<String, Object> metadata;

    /**
     * 构造控制器并在启动时静态计算 metadata 响应体。
     *
     * <p>由于 RFC 9728 metadata 在服务运行期间相对稳定，这里采取 eager 编码以换取热路径的零开销；
     * 若 oauth 配置发生变更需要重启应用生效。</p>
     *
     * @param properties MCP 服务配置，必须已开启 OAuth 且配置了合法 {@code resource} URI
     * @throws IllegalArgumentException 当 {@code oauth.resource} 为空时
     */
    public McpOAuthMetadataController(McpServerProperties properties) {
        McpServerProperties.OAuth oauth = properties.getOauth();
        URI resource = URI.create(oauth.getResource());
        this.metadata = McpProtectedResourceMetadataCodec.encode(
                new McpProtectedResourceMetadata(
                        resource,
                        oauth.getAuthorizationServers().stream().map(URI::create).toList(),
                        oauth.getScopesSupported(),
                        Map.of()));
    }

    /**
     * 返回 Protected Resource Metadata 文档。
     *
     * @return 符合 RFC 9728 的 JSON 文档（{@code resource}、{@code authorization_servers}、
     *         {@code scopes_supported} 等字段）
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> get() {
        return metadata;
    }
}
