package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.security.oauth.McpProtectedResourceMetadata;
import cn.richie696.component.mcp.security.oauth.McpProtectedResourceMetadataCodec;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/** Serves RFC 9728 Protected Resource Metadata for the MCP resource server. */
@RestController
@RequestMapping("${platform.component.mcp.server.oauth.metadata-path:/.well-known/oauth-protected-resource}")
public final class McpOAuthMetadataController {
    private final Map<String, Object> metadata;

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

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> get() {
        return metadata;
    }
}
