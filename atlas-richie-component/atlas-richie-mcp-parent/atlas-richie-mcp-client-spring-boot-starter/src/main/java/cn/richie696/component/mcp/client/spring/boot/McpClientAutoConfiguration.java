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
 * MCP Client Starter 自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(McpHttpToolClient.class)
@ConditionalOnProperty(prefix = McpClientProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HttpClient mcpHttpClient(McpClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

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

    @Bean
    @ConditionalOnMissingBean
    public McpProtocolEraCache mcpProtocolEraCache() {
        return new McpProtocolEraCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpClientResultCache mcpClientResultCache() {
        return new McpClientResultCache();
    }

    @Bean
    @ConditionalOnProperty(prefix = McpClientProperties.PREFIX + ".oauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public McpOAuthTokenClient mcpOAuthTokenClient(HttpClient httpClient, McpClientProperties properties) {
        return new McpOAuthTokenClient(httpClient, properties.getRequestTimeout());
    }

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

    @Bean
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
