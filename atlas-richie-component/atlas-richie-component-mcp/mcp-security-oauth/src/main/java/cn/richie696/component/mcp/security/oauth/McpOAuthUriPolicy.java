package cn.richie696.component.mcp.security.oauth;

import java.net.URI;

/**
 * OAuth metadata URL 的 SSRF 安全边界 SPI。
 */
@FunctionalInterface
public interface McpOAuthUriPolicy {
    void validate(URI uri);

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
