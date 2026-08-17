package cn.richie696.component.mcp.server.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.time.Duration;

/**
 * MCP Server Starter 的配置属性根。
 *
 * <p>绑定前缀 {@value #PREFIX}，包含三块配置：
 * <ul>
 *     <li>服务端元数据（{@code name / version / path / title / description / websiteUrl} 等）；</li>
 *     <li>{@link OAuth} 子节点：仅当 {@code oauth.enabled=true} 且配置了 {@code resource} 时
 *         才会注册 {@code /.well-known/oauth-authorization-server} 元数据端点；</li>
 *     <li>{@link Tools} 子节点：{@code @McpTool} 扫描策略、Tool 默认值、
 *         显式 {@code definitions} 与 {@code overrides}。</li>
 * </ul>
 *
 * <p>所有可变集合（{@link List} / {@link Set} / {@link Map}）在 getter 中返回不可变副本，
 * 避免业务代码持有引用后绕过 Spring 的不可变语义；setter 接受 {@code null} 时回退为空集合。</p>
 *
 * @author richie696
 * @since 2026-08-11
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
    private Tools tools = new Tools();

    /**
     * 是否启用 MCP Server Starter；缺省 {@code true}。
     *
     * @return 当前是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Starter 启用开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 MCP HTTP 端点路径（以 {@code /} 开头）。
     *
     * @return 当前路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置 MCP HTTP 端点路径；必须以 {@code /} 开头，否则启动失败。
     *
     * @param path 新路径
     * @throws IllegalArgumentException 当 {@code path} 为空或不以 {@code /} 开头时
     */
    public void setPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("MCP server path must start with '/'");
        }
        this.path = path;
    }

    /**
     * 获取 MCP Server 名称（用于协议握手）。
     *
     * @return 服务名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置 MCP Server 名称。
     *
     * @param name 新名称
     * @throws IllegalArgumentException 当 {@code name} 为空时
     */
    public void setName(String name) {
        this.name = required(name, "name");
    }

    /**
     * 获取 MCP Server 版本号。
     *
     * @return 版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置 MCP Server 版本号。
     *
     * @param version 新版本号
     * @throws IllegalArgumentException 当 {@code version} 为空时
     */
    public void setVersion(String version) {
        this.version = required(version, "version");
    }

    /**
     * 获取可读的标题。
     *
     * @return 标题（可为空）
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置标题。
     *
     * @param title 新标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取可读的描述信息。
     *
     * @return 描述（可为空）
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置描述信息。
     *
     * @param description 新描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取对外展示的官网 URL。
     *
     * @return 官网 URL（可为空）
     */
    public String getWebsiteUrl() {
        return websiteUrl;
    }

    /**
     * 设置官网 URL。
     *
     * @param websiteUrl 新 URL
     */
    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    /**
     * 获取允许跨域访问的 Origin 列表（不可变副本）。
     *
     * @return 允许的 Origin 列表
     */
    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    /**
     * 设置允许跨域访问的 Origin 列表。{@code null} 视为空。
     *
     * @param allowedOrigins 新 Origin 列表
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }

    /**
     * 获取 OAuth 元数据配置。
     *
     * @return OAuth 子配置
     */
    public OAuth getOauth() {
        return oauth;
    }

    /**
     * 设置 OAuth 元数据配置。{@code null} 视为默认配置。
     *
     * @param oauth 新 OAuth 配置
     */
    public void setOauth(OAuth oauth) {
        this.oauth = oauth == null ? new OAuth() : oauth;
    }

    /**
     * 获取 Tool 相关配置。
     *
     * @return Tools 子配置
     */
    public Tools getTools() {
        return tools;
    }

    /**
     * 设置 Tool 相关配置。{@code null} 视为默认配置。
     *
     * @param tools 新 Tools 配置
     */
    public void setTools(Tools tools) {
        this.tools = tools == null ? new Tools() : tools;
    }

    /**
     * Tool 注册相关配置（{@code platform.component.mcp.server.tools.*}）。
     */
    public static class Tools {
        private boolean enabled = true;
        private List<String> scanPackages = new ArrayList<>();
        private List<String> excludePackages = new ArrayList<>();
        private Set<String> beanNames = new LinkedHashSet<>();
        private Set<String> excludeBeanNames = new LinkedHashSet<>();
        private Set<String> enabledGroups = new LinkedHashSet<>();
        private boolean failFast = true;
        private boolean refreshEnabled;
        private ToolDefaults defaults = new ToolDefaults();
        private Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
        private Map<String, ToolOverride> overrides = new LinkedHashMap<>();

        /**
         * 是否启用 {@code @McpTool} 自动扫描与注册。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 {@code @McpTool} 自动扫描与注册。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取扫描包前缀列表（不可变副本）。
         *
         * @return 扫描包列表
         */
        public List<String> getScanPackages() {
            return List.copyOf(scanPackages);
        }

        /**
         * 设置扫描包前缀列表。
         *
         * @param scanPackages 新扫描包列表
         */
        public void setScanPackages(List<String> scanPackages) {
            this.scanPackages = copyList(scanPackages);
        }

        /**
         * 获取排除扫描包列表（不可变副本）。
         *
         * @return 排除包列表
         */
        public List<String> getExcludePackages() {
            return List.copyOf(excludePackages);
        }

        /**
         * 设置排除扫描包列表。
         *
         * @param excludePackages 新排除包列表
         */
        public void setExcludePackages(List<String> excludePackages) {
            this.excludePackages = copyList(excludePackages);
        }

        /**
         * 获取显式指定的 Bean 名称列表（不可变副本）。
         *
         * @return Bean 名集合
         */
        public Set<String> getBeanNames() {
            return Set.copyOf(beanNames);
        }

        /**
         * 设置显式指定的 Bean 名称列表。
         *
         * @param beanNames 新 Bean 名集合
         */
        public void setBeanNames(Set<String> beanNames) {
            this.beanNames = copySet(beanNames);
        }

        /**
         * 获取显式排除的 Bean 名称列表（不可变副本）。
         *
         * @return 排除 Bean 名集合
         */
        public Set<String> getExcludeBeanNames() {
            return Set.copyOf(excludeBeanNames);
        }

        /**
         * 设置显式排除的 Bean 名称列表。
         *
         * @param excludeBeanNames 新排除 Bean 名集合
         */
        public void setExcludeBeanNames(Set<String> excludeBeanNames) {
            this.excludeBeanNames = copySet(excludeBeanNames);
        }

        /**
         * 获取启用的 Tool 分组列表（不可变副本）。
         *
         * @return 启用分组集合
         */
        public Set<String> getEnabledGroups() {
            return Set.copyOf(enabledGroups);
        }

        /**
         * 设置启用的 Tool 分组列表。
         *
         * @param enabledGroups 新启用分组集合
         */
        public void setEnabledGroups(Set<String> enabledGroups) {
            this.enabledGroups = copySet(enabledGroups);
        }

        /**
         * 是否在 Tool 注册阶段遇到错误时立即启动失败（缺省 {@code true}）。
         *
         * @return 是否快速失败
         */
        public boolean isFailFast() {
            return failFast;
        }

        /**
         * 设置是否快速失败。
         *
         * @param failFast 是否快速失败
         */
        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }

        /**
         * 是否启用运行时刷新（监听配置中心事件）。
         *
         * @return 是否启用刷新
         */
        public boolean isRefreshEnabled() {
            return refreshEnabled;
        }

        /**
         * 设置是否启用运行时刷新。
         *
         * @param refreshEnabled 是否启用刷新
         */
        public void setRefreshEnabled(boolean refreshEnabled) {
            this.refreshEnabled = refreshEnabled;
        }

        /**
         * 获取 Tool 默认值配置。
         *
         * @return ToolDefaults 实例
         */
        public ToolDefaults getDefaults() {
            return defaults;
        }

        /**
         * 设置 Tool 默认值配置。{@code null} 视为默认配置。
         *
         * @param defaults 新 ToolDefaults
         */
        public void setDefaults(ToolDefaults defaults) {
            this.defaults = defaults == null ? new ToolDefaults() : defaults;
        }

        /**
         * 获取显式声明的 Tool 定义集合（不可变副本）。
         *
         * @return Tool 定义集合
         */
        public Map<String, ToolDefinition> getDefinitions() {
            return Map.copyOf(definitions);
        }

        /**
         * 设置显式声明的 Tool 定义集合。
         *
         * @param definitions 新 Tool 定义集合
         */
        public void setDefinitions(Map<String, ToolDefinition> definitions) {
            this.definitions = copyMap(definitions);
        }

        /**
         * 获取针对已有 Tool 的覆写集合（不可变副本）。
         *
         * @return Tool 覆写集合
         */
        public Map<String, ToolOverride> getOverrides() {
            return Map.copyOf(overrides);
        }

        /**
         * 设置针对已有 Tool 的覆写集合。
         *
         * @param overrides 新 Tool 覆写集合
         */
        public void setOverrides(Map<String, ToolOverride> overrides) {
            this.overrides = copyMap(overrides);
        }
    }

    /**
     * Tool 默认值（应用于所有未显式声明的 Tool）。
     */
    public static class ToolDefaults {
        private Duration timeout;
        private boolean auditEnabled;

        /**
         * 获取默认单次 Tool 调用的超时时间。
         *
         * @return 超时时长，可为 {@code null} 表示不限制
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * 设置默认超时时间，必须为正。
         *
         * @param timeout 新超时时长
         * @throws IllegalArgumentException 当 {@code timeout} 为 0 或负数时
         */
        public void setTimeout(Duration timeout) {
            if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
                throw new IllegalArgumentException("MCP default tool timeout must be positive");
            }
            this.timeout = timeout;
        }

        /**
         * 是否默认开启审计。
         *
         * @return 是否启用审计
         */
        public boolean isAuditEnabled() {
            return auditEnabled;
        }

        /**
         * 设置是否默认开启审计。
         *
         * @param auditEnabled 是否启用审计
         */
        public void setAuditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
        }
    }

    /**
     * 显式声明的 Tool 定义；继承 {@link ToolOverride} 的可覆写字段，并新增 {@code handlerRef}。
     */
    public static class ToolDefinition extends ToolOverride {
        private String handlerRef;

        /**
         * 获取 Handler 引用名（指向
         * {@link cn.richie696.component.mcp.api.server.McpToolHandlerProvider}）。
         *
         * @return Handler 引用
         */
        public String getHandlerRef() {
            return handlerRef;
        }

        /**
         * 设置 Handler 引用名。
         *
         * @param handlerRef 新 Handler 引用
         */
        public void setHandlerRef(String handlerRef) {
            this.handlerRef = handlerRef;
        }
    }

    /**
     * 可空补丁模型：{@code null} 字段表示保留低优先级原值。
     *
     * <p>用于 {@code overrides}：仅覆写非空字段，避免每次全部覆盖造成配置膨胀。</p>
     */
    public static class ToolOverride {
        private Boolean enabled;
        private String title;
        private String description;
        private String group;
        private Duration timeout;
        private Boolean auditEnabled;
        private Set<String> requiredScopes;
        private Map<String, Object> annotations;
        private Map<String, Object> policies;
        private Map<String, Object> inputSchema;
        private Map<String, Object> outputSchema;

        /**
         * 获取 enabled 覆写。
         *
         * @return enabled 覆写值，可为 {@code null}
         */
        public Boolean getEnabled() { return enabled; }
        /**
         * 设置 enabled 覆写。
         *
         * @param enabled 新值，可为 {@code null} 表示不修改
         */
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        /**
         * 获取 title 覆写。
         *
         * @return title 覆写值
         */
        public String getTitle() { return title; }
        /**
         * 设置 title 覆写。
         *
         * @param title 新值
         */
        public void setTitle(String title) { this.title = title; }
        /**
         * 获取 description 覆写。
         *
         * @return description 覆写值
         */
        public String getDescription() { return description; }
        /**
         * 设置 description 覆写。
         *
         * @param description 新值
         */
        public void setDescription(String description) { this.description = description; }
        /**
         * 获取 group 覆写。
         *
         * @return group 覆写值
         */
        public String getGroup() { return group; }
        /**
         * 设置 group 覆写。
         *
         * @param group 新值
         */
        public void setGroup(String group) { this.group = group; }
        /**
         * 获取 timeout 覆写。
         *
         * @return timeout 覆写值
         */
        public Duration getTimeout() { return timeout; }
        /**
         * 设置 timeout 覆写，必须为正。
         *
         * @param timeout 新超时
         * @throws IllegalArgumentException 当 {@code timeout} 为 0 或负数时
         */
        public void setTimeout(Duration timeout) {
            if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
                throw new IllegalArgumentException("MCP tool timeout must be positive");
            }
            this.timeout = timeout;
        }
        /**
         * 获取 auditEnabled 覆写。
         *
         * @return auditEnabled 覆写值
         */
        public Boolean getAuditEnabled() { return auditEnabled; }
        /**
         * 设置 auditEnabled 覆写。
         *
         * @param auditEnabled 新值
         */
        public void setAuditEnabled(Boolean auditEnabled) { this.auditEnabled = auditEnabled; }
        /**
         * 获取所需 scope 列表（不可变副本）。
         *
         * @return scope 列表，可为 {@code null}
         */
        public Set<String> getRequiredScopes() {
            return requiredScopes == null ? null : Set.copyOf(requiredScopes);
        }
        /**
         * 设置所需 scope 列表。
         *
         * @param requiredScopes 新 scope 列表
         */
        public void setRequiredScopes(Set<String> requiredScopes) {
            this.requiredScopes = requiredScopes == null ? null : copySet(requiredScopes);
        }
        /**
         * 获取 annotations 覆写（不可变副本）。
         *
         * @return annotations，可为 {@code null}
         */
        public Map<String, Object> getAnnotations() {
            return annotations == null ? null : Map.copyOf(annotations);
        }
        /**
         * 设置 annotations 覆写。
         *
         * @param annotations 新 annotations
         */
        public void setAnnotations(Map<String, Object> annotations) {
            this.annotations = annotations == null ? null : copyObjectMap(annotations);
        }
        /**
         * 获取 policies 覆写（不可变副本）。
         *
         * @return policies，可为 {@code null}
         */
        public Map<String, Object> getPolicies() {
            return policies == null ? null : Map.copyOf(policies);
        }
        /**
         * 设置 policies 覆写。
         *
         * @param policies 新 policies
         */
        public void setPolicies(Map<String, Object> policies) {
            this.policies = policies == null ? null : copyObjectMap(policies);
        }
        /**
         * 获取 inputSchema 覆写（不可变副本）。
         *
         * @return inputSchema，可为 {@code null}
         */
        public Map<String, Object> getInputSchema() {
            return inputSchema == null ? null : Map.copyOf(inputSchema);
        }
        /**
         * 设置 inputSchema 覆写。
         *
         * @param inputSchema 新 inputSchema
         */
        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema == null ? null : copyObjectMap(inputSchema);
        }
        /**
         * 获取 outputSchema 覆写（不可变副本）。
         *
         * @return outputSchema，可为 {@code null}
         */
        public Map<String, Object> getOutputSchema() {
            return outputSchema == null ? null : Map.copyOf(outputSchema);
        }
        /**
         * 设置 outputSchema 覆写。
         *
         * @param outputSchema 新 outputSchema
         */
        public void setOutputSchema(Map<String, Object> outputSchema) {
            this.outputSchema = outputSchema == null ? null : copyObjectMap(outputSchema);
        }
    }

    /**
     * OAuth 元数据配置（{@code platform.component.mcp.server.oauth.*}）。
     */
    public static class OAuth {
        private boolean enabled;
        private String resource;
        private List<String> authorizationServers = new ArrayList<>();
        private List<String> scopesSupported = new ArrayList<>();

        /**
         * 是否启用 OAuth 元数据端点。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 OAuth 元数据端点。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 OAuth resource 标识（通常为 MCP Server 自身的 URL）。
         *
         * @return resource 标识
         */
        public String getResource() {
            return resource;
        }

        /**
         * 设置 OAuth resource 标识；不能为空。
         *
         * @param resource 新 resource 标识
         * @throws IllegalArgumentException 当 {@code resource} 为空时
         */
        public void setResource(String resource) {
            if (resource == null || resource.isBlank()) {
                throw new IllegalArgumentException("MCP OAuth resource must not be blank");
            }
            this.resource = resource;
        }

        /**
         * 获取授权服务器列表（不可变副本）。
         *
         * @return authorization server 列表
         */
        public List<String> getAuthorizationServers() {
            return List.copyOf(authorizationServers);
        }

        /**
         * 设置授权服务器列表。
         *
         * @param authorizationServers 新 authorization server 列表
         */
        public void setAuthorizationServers(List<String> authorizationServers) {
            this.authorizationServers = authorizationServers == null
                    ? new ArrayList<>() : new ArrayList<>(authorizationServers);
        }

        /**
         * 获取支持的 scope 列表（不可变副本）。
         *
         * @return scopes supported 列表
         */
        public List<String> getScopesSupported() {
            return List.copyOf(scopesSupported);
        }

        /**
         * 设置支持的 scope 列表。
         *
         * @param scopesSupported 新 scopes supported 列表
         */
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

    private static List<String> copyList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static Set<String> copySet(Set<String> values) {
        return values == null ? new LinkedHashSet<>() : new LinkedHashSet<>(values);
    }

    private static <T> Map<String, T> copyMap(Map<String, T> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }
}
