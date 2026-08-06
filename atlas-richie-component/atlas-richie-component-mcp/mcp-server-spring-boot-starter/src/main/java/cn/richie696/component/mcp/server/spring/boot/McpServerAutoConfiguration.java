package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidator;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy;
import cn.richie696.component.mcp.server.resource.McpResourceRegistration;
import cn.richie696.component.mcp.server.resource.McpResourceRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceTemplateRegistration;
import cn.richie696.component.mcp.server.resource.McpResourceVisibilityPolicy;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistration;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistry;
import cn.richie696.component.mcp.server.completion.McpCompletionRegistry;
import cn.richie696.component.mcp.api.server.McpCompletionHandler;
import cn.richie696.component.mcp.transport.http.McpOriginPolicy;
import cn.richie696.component.mcp.transport.http.McpServerHttpEndpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;

/**
 * MCP Server Starter 自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(McpServerHttpEndpoint.class)
@ConditionalOnProperty(prefix = McpServerProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpToolRegistry mcpToolRegistry(
            ObjectProvider<McpToolRegistration> registrations,
            ObjectProvider<McpToolVisibilityPolicy> visibilityPolicies,
            ObjectProvider<McpJsonSchemaValidator> schemaValidators,
            ApplicationContext applicationContext) {
        McpToolVisibilityPolicy visibilityPolicy = visibilityPolicies.getIfAvailable(
                () -> McpToolVisibilityPolicy.ALLOW_ALL);
        McpJsonSchemaValidator schemaValidator = schemaValidators.getIfAvailable();
        McpToolRegistry registry = schemaValidator == null
                ? new McpToolRegistry(visibilityPolicy)
                : new McpToolRegistry(visibilityPolicy, schemaValidator);
        registrations.orderedStream().forEach(registry::register);
        new McpAnnotatedToolRegistrar(applicationContext).registerInto(registry);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public McpResourceRegistry mcpResourceRegistry(
            ObjectProvider<McpResourceRegistration> registrations,
            ObjectProvider<McpResourceTemplateRegistration> templateRegistrations,
            ObjectProvider<McpResourceVisibilityPolicy> visibilityPolicies) {
        McpResourceRegistry registry = new McpResourceRegistry(
                visibilityPolicies.getIfAvailable(() -> McpResourceVisibilityPolicy.ALLOW_ALL));
        registrations.orderedStream().forEach(registry::register);
        templateRegistrations.orderedStream().forEach(registry::registerTemplate);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public McpPromptRegistry mcpPromptRegistry(ObjectProvider<McpPromptRegistration> registrations) {
        McpPromptRegistry registry = new McpPromptRegistry();
        registrations.orderedStream().forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnBean(McpCompletionHandler.class)
    @ConditionalOnMissingBean
    public McpCompletionRegistry mcpCompletionRegistry(ObjectProvider<McpCompletionHandler> handlers) {
        return new McpCompletionRegistry(handlers.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpOriginPolicy mcpOriginPolicy(McpServerProperties properties) {
        return origin -> properties.getAllowedOrigins().isEmpty()
                || properties.getAllowedOrigins().contains(origin);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpImplementationInfo mcpServerImplementationInfo(McpServerProperties properties) {
        return new McpImplementationInfo(
                properties.getName(),
                properties.getVersion(),
                properties.getTitle(),
                properties.getDescription(),
                properties.getWebsiteUrl(),
                java.util.List.of());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpServerHttpEndpoint mcpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo implementationInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            ObjectProvider<McpCompletionRegistry> completionRegistries) {
        return new McpServerHttpEndpoint(
                registry, implementationInfo, originPolicy, resourceRegistry, promptRegistry,
                completionRegistries.getIfAvailable());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public McpServerHttpController mcpServerHttpController(McpServerHttpEndpoint endpoint) {
        return new McpServerHttpController(endpoint);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnMissingBean
    public McpServerWebFluxController mcpServerWebFluxController(McpServerHttpEndpoint endpoint) {
        return new McpServerWebFluxController(endpoint);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.PREFIX + ".oauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public McpOAuthMetadataController mcpOAuthMetadataController(McpServerProperties properties) {
        if (properties.getOauth().getResource() == null || properties.getOauth().getResource().isBlank()) {
            throw new IllegalArgumentException(
                    "MCP OAuth metadata requires platform.component.mcp.server.oauth.resource");
        }
        return new McpOAuthMetadataController(properties);
    }
}
