package cn.richie696.component.mcp.server.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP Server Starter 配置。
 */
@ConfigurationProperties(prefix = McpServerProperties.PREFIX)
public class McpServerProperties {
    public static final String PREFIX = "platform.component.mcp.server";

    private boolean enabled = true;
    private String path = "/mcp";
    private String name = "atlas-richie-mcp-server";
    private String version = "1.0.0";
    private String title;
    private String description;
    private String websiteUrl;
    private List<String> allowedOrigins = new ArrayList<>();
    private OAuth oauth = new OAuth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("MCP server path must start with '/'");
        }
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = required(name, "name");
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = required(version, "version");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }

    public OAuth getOauth() {
        return oauth;
    }

    public void setOauth(OAuth oauth) {
        this.oauth = oauth == null ? new OAuth() : oauth;
    }

    public static class OAuth {
        private boolean enabled;
        private String resource;
        private List<String> authorizationServers = new ArrayList<>();
        private List<String> scopesSupported = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            if (resource == null || resource.isBlank()) {
                throw new IllegalArgumentException("MCP OAuth resource must not be blank");
            }
            this.resource = resource;
        }

        public List<String> getAuthorizationServers() {
            return List.copyOf(authorizationServers);
        }

        public void setAuthorizationServers(List<String> authorizationServers) {
            this.authorizationServers = authorizationServers == null
                    ? new ArrayList<>() : new ArrayList<>(authorizationServers);
        }

        public List<String> getScopesSupported() {
            return List.copyOf(scopesSupported);
        }

        public void setScopesSupported(List<String> scopesSupported) {
            this.scopesSupported = scopesSupported == null
                    ? new ArrayList<>() : new ArrayList<>(scopesSupported);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MCP server " + field + " must not be blank");
        }
        return value;
    }
}
