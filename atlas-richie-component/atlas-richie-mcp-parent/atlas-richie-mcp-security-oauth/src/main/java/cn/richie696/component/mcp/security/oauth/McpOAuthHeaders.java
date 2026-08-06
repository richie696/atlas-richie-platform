package cn.richie696.component.mcp.security.oauth;

import java.util.List;
import java.util.Objects;

/**
 * MCP OAuth 标准 HTTP Header 生成器。
 */
public final class McpOAuthHeaders {
    private McpOAuthHeaders() {
    }

    public static String bearer(McpOAuthAccessToken token) {
        return Objects.requireNonNull(token, "token").authorizationHeader();
    }

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
