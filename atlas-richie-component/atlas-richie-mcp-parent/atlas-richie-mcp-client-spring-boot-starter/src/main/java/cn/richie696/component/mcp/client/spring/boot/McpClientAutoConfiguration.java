package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.api.McpOperations;
import cn.richie696.component.mcp.protocol.compatibility.McpProtocolEraCache;
import cn.richie696.component.mcp.security.oauth.McpOAuthTokenProvider;
import cn.richie696.component.mcp.security.oauth.McpOAuthTokenClient;
import cn.richie696.component.mcp.security.oauth.McpOAuthTokenManager;
import cn.richie696.component.mcp.transport.http.McpHttpToolClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.net.URI;
import java.time.Duration;

/**
 * MCP Client Spring Boot Starter 的自动配置入口。
 *
 * <p>本类承担"装配门面"角色：按条件注册 {@link java.net.http.HttpClient}、{@link McpHttpToolClient}、
 * 协议 Era 缓存、结果缓存、可选 OAuth 客户端/Token 管理器，以及对外暴露的
 * {@link McpHttpOperations}（同时挂到 {@code mcpOperations} 与 {@code mcpDynamicOperations} 两个 bean name 上，
 * 便于依赖注入场景按接口维度检索）。</p>
 *
 * <p>启用条件：{@code platform.component.mcp.client.enabled=true}（缺省 true）且 classpath 含
 * {@link McpHttpToolClient}。OAuth 相关 bean 仅在 {@code platform.component.mcp.client.oauth.enabled=true} 时注入，
 * 避免在不需要 OAuth 的环境强制要求 token endpoint / clientId。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@AutoConfiguration
@ConditionalOnClass(McpHttpToolClient.class)
@ConditionalOnProperty(prefix = McpClientProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    /**
     * 注册共享的 JDK {@link HttpClient}，使用配置的 {@code connectTimeout}。
     *
     * @param properties MCP 客户端配置
     * @return JDK HttpClient 单例
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpClient mcpHttpClient(McpClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    /**
     * 注册 {@link McpHttpToolClient}，把协议版本、分页上限、超时等关键参数从配置透传。
     *
     * @param httpClient 共享 JDK HttpClient
     * @param properties MCP 客户端配置
     * @return 装配完成的 {@link McpHttpToolClient}
     */
    @Bean
    @ConditionalOnMissingBean
    public McpHttpToolClient mcpHttpToolClient(
            HttpClient httpClient,
            McpClientProperties properties) {
        return new McpHttpToolClient(
                httpClient,
                properties.getRequestTimeout(),
                properties.getName(),
                properties.getVersion(),
                properties.getPreferredProtocolVersion(),
                properties.getMaxPages(),
                properties.getMaxItems());
    }

    /**
     * 注册协议 Era 缓存（按 server 维度缓存"协商后的协议版本"）。
     *
     * @return 默认 {@link McpProtocolEraCache} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public McpProtocolEraCache mcpProtocolEraCache() {
        return new McpProtocolEraCache();
    }

    /**
     * 注册 list/discovery 结果缓存。
     *
     * @return 默认 {@link McpClientResultCache} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public McpClientResultCache mcpClientResultCache() {
        return new McpClientResultCache();
    }

    /**
     * 当 {@code platform.component.mcp.client.oauth.enabled=true} 时，注册 OAuth Token 客户端。
     *
     * @param httpClient 共享 JDK HttpClient
     * @param properties MCP 客户端配置
     * @return {@link McpOAuthTokenClient} 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = McpClientProperties.PREFIX + ".oauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public McpOAuthTokenClient mcpOAuthTokenClient(HttpClient httpClient, McpClientProperties properties) {
        return new McpOAuthTokenClient(httpClient, properties.getRequestTimeout());
    }

    /**
     * 当 OAuth 启用时，注册 Token 管理器，负责 token 缓存与刷新。
     *
     * <p>在 OAuth 启用但 tokenEndpoint / clientId / resource 任一缺失时直接 fail-fast，
     * 避免运行期才暴露配置错误。</p>
     *
     * @param tokenClient OAuth Token 客户端
     * @param properties  MCP 客户端配置
     * @return {@link McpOAuthTokenManager} 实例
     * @throws IllegalArgumentException 当 OAuth 必填字段缺失时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = McpClientProperties.PREFIX + ".oauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(McpOAuthTokenProvider.class)
    public McpOAuthTokenManager mcpOAuthTokenManager(
            McpOAuthTokenClient tokenClient,
            McpClientProperties properties) {
        McpClientProperties.OAuth oauth = properties.getOauth();
        if (oauth.getTokenEndpoint() == null || oauth.getClientId() == null || oauth.getResource() == null) {
            throw new IllegalArgumentException(
                    "MCP OAuth requires token-endpoint, client-id and resource when enabled");
        }
        return new McpOAuthTokenManager(
                tokenClient,
                URI.create(oauth.getTokenEndpoint()),
                oauth.getClientId(),
                oauth.getClientSecret(),
                URI.create(oauth.getResource()),
                oauth.getScopes());
    }

    /**
     * 注册对外的 MCP 客户端能力门面，bean name 同时为 {@code mcpOperations} 与 {@code mcpDynamicOperations}，
     * 便于按 {@code @Qualifier("mcpOperations")} 或 {@code @Qualifier("mcpDynamicOperations")} 区分调用。
     *
     * @param client          HTTP 工具客户端
     * @param properties      MCP 客户端配置
     * @param protocolEraCache 协议 Era 缓存
     * @param resultCache     结果缓存
     * @param tokenProviders  可选 OAuth Token Provider，使用 {@link org.springframework.beans.factory.ObjectProvider#getIfAvailable()} 兼容未启用 OAuth 的场景
     * @return {@link McpHttpOperations} 实例
     */
    @Bean(name = {"mcpOperations", "mcpDynamicOperations"})
    @ConditionalOnMissingBean(McpOperations.class)
    public McpHttpOperations mcpOperations(
            McpHttpToolClient client,
            McpClientProperties properties,
            McpProtocolEraCache protocolEraCache,
            McpClientResultCache resultCache,
            org.springframework.beans.factory.ObjectProvider<McpOAuthTokenProvider> tokenProviders) {
        return new McpHttpOperations(
                client, properties, protocolEraCache, resultCache, tokenProviders.getIfAvailable());
    }
}
