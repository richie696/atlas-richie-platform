package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Client Starter 配置。
 */
@ConfigurationProperties(prefix = McpClientProperties.PREFIX)
public class McpClientProperties {
    public static final String PREFIX = "platform.component.mcp.client";

    private boolean enabled = true;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private String name = "atlas-richie-mcp-client";
    private String version = "1.0.0";
    private String preferredProtocolVersion = McpProtocolVersions.V_2026_07_28;
    private boolean negotiateProtocol = true;
    private Duration negotiationTtl = Duration.ofMinutes(5);
    private boolean resultCacheEnabled = true;
    private Duration resultCacheTtl = Duration.ofSeconds(60);
    private int maxPages = 100;
    private int maxItems = 10_000;
    private OAuth oauth = new OAuth();
    private Map<String, Server> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = positive(connectTimeout, "connectTimeout");
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = positive(requestTimeout, "requestTimeout");
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

    public String getPreferredProtocolVersion() {
        return preferredProtocolVersion;
    }

    public void setPreferredProtocolVersion(String preferredProtocolVersion) {
        String value = required(preferredProtocolVersion, "preferredProtocolVersion");
        if (!McpProtocolVersions.SUPPORTED.contains(value)) {
            throw new IllegalArgumentException("Unsupported MCP preferredProtocolVersion: " + value);
        }
        this.preferredProtocolVersion = value;
    }

    public boolean isNegotiateProtocol() {
        return negotiateProtocol;
    }

    public void setNegotiateProtocol(boolean negotiateProtocol) {
        this.negotiateProtocol = negotiateProtocol;
    }

    public Duration getNegotiationTtl() {
        return negotiationTtl;
    }

    public void setNegotiationTtl(Duration negotiationTtl) {
        this.negotiationTtl = positive(negotiationTtl, "negotiationTtl");
    }

    public boolean isResultCacheEnabled() {
        return resultCacheEnabled;
    }

    public void setResultCacheEnabled(boolean resultCacheEnabled) {
        this.resultCacheEnabled = resultCacheEnabled;
    }

    public Duration getResultCacheTtl() {
        return resultCacheTtl;
    }

    public void setResultCacheTtl(Duration resultCacheTtl) {
        this.resultCacheTtl = positive(resultCacheTtl, "resultCacheTtl");
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        if (maxPages < 1 || maxPages > 10_000) throw new IllegalArgumentException("maxPages must be 1..10000");
        this.maxPages = maxPages;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        if (maxItems < 1 || maxItems > 1_000_000) throw new IllegalArgumentException("maxItems must be 1..1000000");
        this.maxItems = maxItems;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public void setOauth(OAuth oauth) {
        this.oauth = oauth == null ? new OAuth() : oauth;
    }

    public Map<String, Server> getServers() {
        return Map.copyOf(servers);
    }

    public void setServers(Map<String, Server> servers) {
        this.servers = servers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(servers);
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MCP client " + field + " must not be blank");
        }
        return value;
    }

    public static class Server {
        private String endpoint;
        private String resource;
        private List<String> scopes = List.of();
        private Map<String, String> headers = new LinkedHashMap<>();

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = required(endpoint, "server endpoint");
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource == null || resource.isBlank() ? null : resource;
        }

        public List<String> getScopes() {
            return List.copyOf(scopes);
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        public Map<String, String> getHeaders() {
            return Map.copyOf(headers);
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }
    }

    public static class OAuth {
        private boolean enabled;
        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private String resource;
        private java.util.Set<String> scopes = java.util.Set.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTokenEndpoint() {
            return tokenEndpoint;
        }

        public void setTokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint == null || tokenEndpoint.isBlank() ? null : tokenEndpoint;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId == null || clientId.isBlank() ? null : clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource == null || resource.isBlank() ? null : resource;
        }

        public java.util.Set<String> getScopes() {
            return java.util.Set.copyOf(scopes);
        }

        public void setScopes(java.util.Set<String> scopes) {
            this.scopes = scopes == null ? java.util.Set.of() : java.util.Set.copyOf(scopes);
        }
    }
}
