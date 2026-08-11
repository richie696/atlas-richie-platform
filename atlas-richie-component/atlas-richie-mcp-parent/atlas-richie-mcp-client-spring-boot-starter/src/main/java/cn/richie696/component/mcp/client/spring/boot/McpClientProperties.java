package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Client Starter 配置树（{@code platform.component.mcp.client.*}）。
 *
 * <p>聚合：开关、连接/请求超时、客户端标识、协议版本与协商策略、结果缓存策略、分页上限、多 server 定义与 OAuth 子配置。
 * 所有 setter 均做"必填/正数/枚举合法"校验，避免业务方在 YAML 中填错值到运行期才暴露。</p>
 *
 * <p>嵌套的两个静态类 {@link Server} 与 {@link OAuth} 分别对应"每个 MCP server 的连接信息"与"OAuth 客户端配置"，
 * 保持单一职责的同时避免引入额外的配置类文件。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@ConfigurationProperties(prefix = McpClientProperties.PREFIX)
public class McpClientProperties {
    /**
     * 配置前缀，对齐中台 {@code platform.component.*} 规范。
     */
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

    /**
     * 是否启用 Starter（默认 true）。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 Starter。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取建立 TCP 连接的超时。
     *
     * @return 连接超时
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 设置建立 TCP 连接的超时（必须为正数）。
     *
     * @param connectTimeout 连接超时
     * @throws IllegalArgumentException 当值为 {@code null}、零或负数时抛出
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = positive(connectTimeout, "connectTimeout");
    }

    /**
     * 获取单次请求的总超时。
     *
     * @return 请求超时
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * 设置单次请求的总超时（必须为正数）。
     *
     * @param requestTimeout 请求超时
     * @throws IllegalArgumentException 当值为 {@code null}、零或负数时抛出
     */
    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = positive(requestTimeout, "requestTimeout");
    }

    /**
     * 获取客户端名称（用于协议握手时的 {@code clientInfo.name}）。
     *
     * @return 客户端名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置客户端名称（不可为空）。
     *
     * @param name 客户端名称
     * @throws IllegalArgumentException 当值为 {@code null} 或空白时抛出
     */
    public void setName(String name) {
        this.name = required(name, "name");
    }

    /**
     * 获取客户端版本（用于协议握手时的 {@code clientInfo.version}）。
     *
     * @return 客户端版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置客户端版本（不可为空）。
     *
     * @param version 客户端版本
     * @throws IllegalArgumentException 当值为 {@code null} 或空白时抛出
     */
    public void setVersion(String version) {
        this.version = required(version, "version");
    }

    /**
     * 获取协商时的优先协议版本。
     *
     * @return 协议版本字符串
     */
    public String getPreferredProtocolVersion() {
        return preferredProtocolVersion;
    }

    /**
     * 设置优先协议版本，必须落在 {@link McpProtocolVersions#SUPPORTED} 枚举集合内。
     *
     * @param preferredProtocolVersion 协议版本字符串
     * @throws IllegalArgumentException 当值缺失或不在支持集合内时抛出
     */
    public void setPreferredProtocolVersion(String preferredProtocolVersion) {
        String value = required(preferredProtocolVersion, "preferredProtocolVersion");
        if (!McpProtocolVersions.SUPPORTED.contains(value)) {
            throw new IllegalArgumentException("Unsupported MCP preferredProtocolVersion: " + value);
        }
        this.preferredProtocolVersion = value;
    }

    /**
     * 是否启用协议协商。
     *
     * @return 协议协商开关
     */
    public boolean isNegotiateProtocol() {
        return negotiateProtocol;
    }

    /**
     * 设置协议协商开关。
     *
     * @param negotiateProtocol 协议协商开关
     */
    public void setNegotiateProtocol(boolean negotiateProtocol) {
        this.negotiateProtocol = negotiateProtocol;
    }

    /**
     * 获取协议协商结果缓存的本地 TTL。
     *
     * @return 协商 TTL
     */
    public Duration getNegotiationTtl() {
        return negotiationTtl;
    }

    /**
     * 设置协议协商结果缓存的本地 TTL（必须为正数）。
     *
     * @param negotiationTtl 协商 TTL
     * @throws IllegalArgumentException 当值为 {@code null}、零或负数时抛出
     */
    public void setNegotiationTtl(Duration negotiationTtl) {
        this.negotiationTtl = positive(negotiationTtl, "negotiationTtl");
    }

    /**
     * 是否启用 list/discovery 结果缓存。
     *
     * @return 结果缓存开关
     */
    public boolean isResultCacheEnabled() {
        return resultCacheEnabled;
    }

    /**
     * 设置 list/discovery 结果缓存开关。
     *
     * @param resultCacheEnabled 结果缓存开关
     */
    public void setResultCacheEnabled(boolean resultCacheEnabled) {
        this.resultCacheEnabled = resultCacheEnabled;
    }

    /**
     * 获取 list/discovery 结果缓存的 TTL。
     *
     * @return 结果缓存 TTL
     */
    public Duration getResultCacheTtl() {
        return resultCacheTtl;
    }

    /**
     * 设置 list/discovery 结果缓存的 TTL（必须为正数）。
     *
     * @param resultCacheTtl 结果缓存 TTL
     * @throws IllegalArgumentException 当值为 {@code null}、零或负数时抛出
     */
    public void setResultCacheTtl(Duration resultCacheTtl) {
        this.resultCacheTtl = positive(resultCacheTtl, "resultCacheTtl");
    }

    /**
     * 获取单次 list 操作允许翻页的最大次数。
     *
     * @return 最大翻页数
     */
    public int getMaxPages() {
        return maxPages;
    }

    /**
     * 设置单次 list 操作允许翻页的最大次数（1..10000）。
     *
     * @param maxPages 最大翻页数
     * @throws IllegalArgumentException 当值越界时抛出
     */
    public void setMaxPages(int maxPages) {
        if (maxPages < 1 || maxPages > 10_000) throw new IllegalArgumentException("maxPages must be 1..10000");
        this.maxPages = maxPages;
    }

    /**
     * 获取单次 list 操作允许返回的最大条目数。
     *
     * @return 最大条目数
     */
    public int getMaxItems() {
        return maxItems;
    }

    /**
     * 设置单次 list 操作允许返回的最大条目数（1..1000000）。
     *
     * @param maxItems 最大条目数
     * @throws IllegalArgumentException 当值越界时抛出
     */
    public void setMaxItems(int maxItems) {
        if (maxItems < 1 || maxItems > 1_000_000) throw new IllegalArgumentException("maxItems must be 1..1000000");
        this.maxItems = maxItems;
    }

    /**
     * 获取 OAuth 子配置，永不为 {@code null}。
     *
     * @return OAuth 子配置
     */
    public OAuth getOauth() {
        return oauth;
    }

    /**
     * 设置 OAuth 子配置；为 {@code null} 时回退为默认空配置。
     *
     * @param oauth OAuth 子配置
     */
    public void setOauth(OAuth oauth) {
        this.oauth = oauth == null ? new OAuth() : oauth;
    }

    /**
     * 获取所有 server 配置的不可变快照。
     *
     * @return 不可变 Map 快照
     */
    public Map<String, Server> getServers() {
        return Map.copyOf(servers);
    }

    /**
     * 设置 server 配置；为 {@code null} 时按空 Map 处理。
     *
     * @param servers server 配置
     */
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

    /**
     * 单个 MCP server 的连接配置：endpoint、资源 URI、scope、附加请求头。
     */
    public static class Server {
        private String endpoint;
        private String resource;
        private List<String> scopes = List.of();
        private Map<String, String> headers = new LinkedHashMap<>();

        /**
         * 获取 server 端点 URL。
         *
         * @return 端点 URL
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * 设置 server 端点 URL（不可为空）。
         *
         * @param endpoint 端点 URL
         * @throws IllegalArgumentException 当值为 {@code null} 或空白时抛出
         */
        public void setEndpoint(String endpoint) {
            this.endpoint = required(endpoint, "server endpoint");
        }

        /**
         * 获取 OAuth 资源 URI。
         *
         * @return 资源 URI，可为 {@code null}
         */
        public String getResource() {
            return resource;
        }

        /**
         * 设置 OAuth 资源 URI；空白值会被规范化为 {@code null}，便于上游做"无则回退到 endpoint"判断。
         *
         * @param resource 资源 URI
         */
        public void setResource(String resource) {
            this.resource = resource == null || resource.isBlank() ? null : resource;
        }

        /**
         * 获取该 server 的 scope 集合（不可变副本）。
         *
         * @return scope 集合
         */
        public List<String> getScopes() {
            return List.copyOf(scopes);
        }

        /**
         * 设置该 server 的 scope 集合；{@code null} 时按空集合处理。
         *
         * @param scopes scope 集合
         */
        public void setScopes(List<String> scopes) {
            this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        /**
         * 获取该 server 的附加请求头（不可变副本）。
         *
         * @return 请求头 Map
         */
        public Map<String, String> getHeaders() {
            return Map.copyOf(headers);
        }

        /**
         * 设置该 server 的附加请求头；{@code null} 时按空 Map 处理。
         *
         * @param headers 请求头 Map
         */
        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }
    }

    /**
     * OAuth 2.1 客户端配置：开关、token endpoint、客户端凭据、资源 URI、scope 集合。
     */
    public static class OAuth {
        private boolean enabled;
        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private String resource;
        private java.util.Set<String> scopes = java.util.Set.of();

        /**
         * 是否启用 OAuth 客户端。
         *
         * @return 启用状态
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 OAuth 客户端。
         *
         * @param enabled 启用状态
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 token endpoint URL。
         *
         * @return token endpoint URL，可为 {@code null}
         */
        public String getTokenEndpoint() {
            return tokenEndpoint;
        }

        /**
         * 设置 token endpoint URL；空白值会被规范化为 {@code null}。
         *
         * @param tokenEndpoint token endpoint URL
         */
        public void setTokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint == null || tokenEndpoint.isBlank() ? null : tokenEndpoint;
        }

        /**
         * 获取 OAuth 客户端 ID。
         *
         * @return 客户端 ID，可为 {@code null}
         */
        public String getClientId() {
            return clientId;
        }

        /**
         * 设置 OAuth 客户端 ID；空白值会被规范化为 {@code null}。
         *
         * @param clientId 客户端 ID
         */
        public void setClientId(String clientId) {
            this.clientId = clientId == null || clientId.isBlank() ? null : clientId;
        }

        /**
         * 获取 OAuth 客户端密钥。
         *
         * @return 客户端密钥
         */
        public String getClientSecret() {
            return clientSecret;
        }

        /**
         * 设置 OAuth 客户端密钥。
         *
         * @param clientSecret 客户端密钥
         */
        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        /**
         * 获取 OAuth 资源 URI（RFC 8707）。
         *
         * @return 资源 URI，可为 {@code null}
         */
        public String getResource() {
            return resource;
        }

        /**
         * 设置 OAuth 资源 URI；空白值会被规范化为 {@code null}。
         *
         * @param resource 资源 URI
         */
        public void setResource(String resource) {
            this.resource = resource == null || resource.isBlank() ? null : resource;
        }

        /**
         * 获取默认 scope 集合（不可变副本）。
         *
         * @return scope 集合
         */
        public java.util.Set<String> getScopes() {
            return java.util.Set.copyOf(scopes);
        }

        /**
         * 设置默认 scope 集合；{@code null} 时按空集合处理。
         *
         * @param scopes scope 集合
         */
        public void setScopes(java.util.Set<String> scopes) {
            this.scopes = scopes == null ? java.util.Set.of() : java.util.Set.copyOf(scopes);
        }
    }
}
