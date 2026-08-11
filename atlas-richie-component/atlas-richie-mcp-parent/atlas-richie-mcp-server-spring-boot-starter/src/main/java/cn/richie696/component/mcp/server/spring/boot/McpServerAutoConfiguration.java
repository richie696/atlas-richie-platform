package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.server.McpArgumentBinder;
import cn.richie696.component.mcp.api.server.McpCallContextFactory;
import cn.richie696.component.mcp.api.server.McpCompletionHandler;
import cn.richie696.component.mcp.api.server.McpToolAuditSink;
import cn.richie696.component.mcp.api.server.McpToolDefinitionSource;
import cn.richie696.component.mcp.api.server.McpToolHandlerProvider;
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.schema.JacksonMcpTypeSchemaGenerator;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidator;
import cn.richie696.component.mcp.schema.McpTypeSchemaGenerator;
import cn.richie696.component.mcp.server.completion.McpCompletionRegistry;
import cn.richie696.component.mcp.server.dispatch.McpAuditInvocationInterceptor;
import cn.richie696.component.mcp.server.dispatch.McpTimeoutInvocationInterceptor;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistration;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceRegistration;
import cn.richie696.component.mcp.server.resource.McpResourceRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceTemplateRegistration;
import cn.richie696.component.mcp.server.resource.McpResourceVisibilityPolicy;
import cn.richie696.component.mcp.server.tool.McpRequiredScopeVisibilityPolicy;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP Server Starter 自动装配入口。
 *
 * <p>启用条件：classpath 存在 {@link McpServerHttpEndpoint}，并且
 * {@code platform.component.mcp.server.enabled=true}（缺省视为开启）。
 * 所有 Bean 均以 {@code @ConditionalOnMissingBean} 暴露，业务可整体覆盖；
 * 通过 {@link EnableConfigurationProperties} 绑定 {@link McpServerProperties}。</p>
 *
 * <p>装配顺序遵循"依赖→被依赖"链路：先注册上下文（{@link McpCallContextFactory}、
 * {@link McpArgumentBinder}、{@link McpTypeSchemaGenerator}），再注册注册表
 * （{@link McpToolRegistry} / {@link McpResourceRegistry} / {@link McpPromptRegistry}），
 * 最后注册端点与控制器（{@link McpServerHttpEndpoint}、
 * {@link McpServerHttpController} 或 {@link McpServerWebFluxController}）。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@AutoConfiguration
@ConditionalOnClass(McpServerHttpEndpoint.class)
@ConditionalOnProperty(prefix = McpServerProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerAutoConfiguration {

    /**
     * 构造 MCP Tool 注册表，并基于可见性策略与 JSON Schema 校验器进行初始化。
     *
     * <p>注册表构造完成后立即执行一次装配（{@link McpToolRegistrationAssembler#assemble()}），
     * 让启动后即可对外提供服务，无需等待首次 refresh。</p>
     *
     * @param visibilityPolicies 业务自定义可见性策略（{@code @Order} 生效）；缺省使用 {@link McpToolVisibilityPolicy#ALLOW_ALL}
     * @param schemaValidators 可选的 JSON Schema 校验器；为空则跳过入参校验
     * @param assembler Tool 装配器，用于初始化注册表
     * @return 已初始化完成的 MCP Tool 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public McpToolRegistry mcpToolRegistry(
            ObjectProvider<McpToolVisibilityPolicy> visibilityPolicies,
            ObjectProvider<McpJsonSchemaValidator> schemaValidators,
            McpToolRegistrationAssembler assembler) {
        McpToolVisibilityPolicy visibilityPolicy = visibilityPolicies.getIfAvailable(
                () -> McpToolVisibilityPolicy.ALLOW_ALL);
        visibilityPolicy = new McpRequiredScopeVisibilityPolicy(visibilityPolicy);
        McpJsonSchemaValidator schemaValidator = schemaValidators.getIfAvailable();
        McpToolRegistry registry = schemaValidator == null
                ? new McpToolRegistry(visibilityPolicy)
                : new McpToolRegistry(visibilityPolicy, schemaValidator);
        registry.replaceAll(assembler.assemble());
        return registry;
    }

    /**
     * 注册超时拦截器（默认拦截器之一）。
     *
     * <p>用户未自行提供 {@link McpTimeoutInvocationInterceptor} 时启用，用于约束单次 Tool 调用的最大耗时。</p>
     *
     * @return 默认实现的超时拦截器
     */
    @Bean
    @ConditionalOnMissingBean(McpTimeoutInvocationInterceptor.class)
    public McpTimeoutInvocationInterceptor mcpTimeoutInvocationInterceptor() {
        return new McpTimeoutInvocationInterceptor();
    }

    /**
     * 当且仅当业务声明了 {@link McpToolAuditSink} 时，注入审计拦截器。
     *
     * <p>避免在不需要审计的环境下产生无效对象，降低内存占用。</p>
     *
     * @param auditSink 业务提供的审计落地实现
     * @return 包装了 {@code auditSink} 的审计拦截器
     */
    @Bean
    @ConditionalOnBean(McpToolAuditSink.class)
    @ConditionalOnMissingBean(McpAuditInvocationInterceptor.class)
    public McpAuditInvocationInterceptor mcpAuditInvocationInterceptor(
            McpToolAuditSink auditSink) {
        return new McpAuditInvocationInterceptor(auditSink);
    }

    /**
     * 注册 Spring 场景下的 Tool 定义刷新器。
     *
     * <p>配合 {@link McpToolRefreshEventBridge} 可实现配置中心推送后自动热刷新，
     * 避免重启服务。{@code @ConditionalOnBean(name = "mcpToolRegistry")} 显式依赖
     * {@link #mcpToolRegistry}，确保装配顺序。</p>
     *
     * @param registry 当前 MCP Tool 注册表
     * @param assembler Tool 装配器
     * @param environment Spring 环境（用于重新绑定配置）
     * @param properties 启动时的初始配置
     * @return 已初始化的刷新器实例
     */
    @Bean
    @ConditionalOnBean(name = "mcpToolRegistry")
    @ConditionalOnMissingBean
    public McpSpringToolDefinitionRefresher mcpSpringToolDefinitionRefresher(
            McpToolRegistry registry,
            McpToolRegistrationAssembler assembler,
            Environment environment,
            McpServerProperties properties) {
        return new McpSpringToolDefinitionRefresher(
                registry, assembler, environment, properties);
    }

    /**
     * 注册事件桥接器（可选）。仅当
     * {@code platform.component.mcp.server.tools.refresh-enabled=true} 时启用，
     * 将 {@code EnvironmentChangeEvent} / 自定义 {@code McpToolDefinitionChangeEvent}
     * 路由到刷新器。
     *
     * @param refresher 由 {@link #mcpSpringToolDefinitionRefresher} 提供的刷新器
     * @return 配置刷新事件桥接器实例
     */
    @Bean
    @ConditionalOnProperty(
            prefix = McpServerProperties.PREFIX + ".tools",
            name = "refresh-enabled",
            havingValue = "true")
    @ConditionalOnBean(McpSpringToolDefinitionRefresher.class)
    @ConditionalOnMissingBean
    public McpToolRefreshEventBridge mcpToolRefreshEventBridge(
            McpSpringToolDefinitionRefresher refresher) {
        return new McpToolRefreshEventBridge(refresher);
    }

    /**
     * 注册 {@code @McpTool} 注解扫描器。
     *
     * <p>从 {@link ApplicationContext} 中扫描所有带 {@code @McpTool} 的 Bean，
     * 与 {@link McpToolRegistrationAssembler} 协同合并多源 Tool 定义。</p>
     *
     * @param applicationContext Spring 应用上下文
     * @param argumentBinder MCP 参数绑定器
     * @param typeSchemaGenerator JSON Schema 生成器
     * @param properties MCP 配置（含 {@code tools.*}）
     * @return 注解扫描器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public McpAnnotatedToolRegistrar mcpAnnotatedToolRegistrar(
            ApplicationContext applicationContext,
            McpArgumentBinder argumentBinder,
            McpTypeSchemaGenerator typeSchemaGenerator,
            McpServerProperties properties) {
        return new McpAnnotatedToolRegistrar(
                applicationContext, argumentBinder, typeSchemaGenerator, properties.getTools());
    }

    /**
     * 注册 Tool 装配器，统一合并：注解扫描 + 编程式 {@link McpToolRegistration} +
     * {@link McpToolHandlerProvider} + {@link McpToolDefinitionSource}。
     *
     * @param registrations 编程式注册的 Tool 列表（按 {@code @Order} 排序）
     * @param annotatedRegistrar 注解扫描器
     * @param handlerProviders 外部 HandlerProvider 列表（按 {@code @Order} 排序）
     * @param definitionSources 外部 DefinitionSource 列表（按 {@code @Order} 排序）
     * @param properties MCP 配置
     * @return 装配器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public McpToolRegistrationAssembler mcpToolRegistrationAssembler(
            ObjectProvider<McpToolRegistration> registrations,
            McpAnnotatedToolRegistrar annotatedRegistrar,
            ObjectProvider<McpToolHandlerProvider> handlerProviders,
            ObjectProvider<McpToolDefinitionSource> definitionSources,
            McpServerProperties properties) {
        return new McpToolRegistrationAssembler(
                registrations.orderedStream().toList(),
                annotatedRegistrar,
                handlerProviders.orderedStream().toList(),
                definitionSources.orderedStream().toList(),
                properties);
    }

    /**
     * 注册基于 Jackson 的 MCP 参数绑定器。
     *
     * <p>当用户未提供 {@link McpArgumentBinder} 且 classpath 存在 Jackson 时生效；
     * 复用用户已配置的 {@link ObjectMapper}，缺省时构造一个新的 {@link JsonMapper}。</p>
     *
     * @param objectMappers Spring 容器中可选的 Jackson {@link ObjectMapper}
     * @return 基于 Jackson 的参数绑定器
     */
    @Bean
    @ConditionalOnMissingBean(McpArgumentBinder.class)
    public McpArgumentBinder mcpArgumentBinder(ObjectProvider<ObjectMapper> objectMappers) {
        return new JacksonMcpArgumentBinder(
                objectMappers.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    /**
     * 注册默认的 MCP 调用上下文工厂（匿名上下文）。
     *
     * @return 匿名 {@link McpCallContextFactory}
     */
    @Bean
    @ConditionalOnMissingBean
    public McpCallContextFactory mcpCallContextFactory() {
        return McpCallContextFactory.anonymous();
    }

    /**
     * 注册基于 Jackson 的 JSON Schema 生成器。
     *
     * @param objectMappers Spring 容器中可选的 {@link ObjectMapper}
     * @return 基于 Jackson 的类型 Schema 生成器
     */
    @Bean
    @ConditionalOnMissingBean(McpTypeSchemaGenerator.class)
    public McpTypeSchemaGenerator mcpTypeSchemaGenerator(
            ObjectProvider<ObjectMapper> objectMappers) {
        return new JacksonMcpTypeSchemaGenerator(
                objectMappers.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    /**
     * 注册 Resource 注册表，统一合并 Resource 与 ResourceTemplate 注册项。
     *
     * @param registrations 编程式注册的 Resource 列表
     * @param templateRegistrations 编程式注册的 ResourceTemplate 列表
     * @param visibilityPolicies 业务自定义可见性策略；缺省 {@link McpResourceVisibilityPolicy#ALLOW_ALL}
     * @return 已注册的 Resource 注册表
     */
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

    /**
     * 注册 Prompt 注册表，统一合并编程式注册的 Prompt 列表。
     *
     * @param registrations 编程式注册的 Prompt 列表
     * @return 已注册的 Prompt 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public McpPromptRegistry mcpPromptRegistry(ObjectProvider<McpPromptRegistration> registrations) {
        McpPromptRegistry registry = new McpPromptRegistry();
        registrations.orderedStream().forEach(registry::register);
        return registry;
    }

    /**
     * 注册 Completion 注册表；仅当用户声明了 {@link McpCompletionHandler} 时启用，
     * 避免空操作污染 Bean 列表。
     *
     * @param handlers 可选的 Completion Handler
     * @return Completion 注册表实例
     */
    @Bean
    @ConditionalOnBean(McpCompletionHandler.class)
    @ConditionalOnMissingBean
    public McpCompletionRegistry mcpCompletionRegistry(ObjectProvider<McpCompletionHandler> handlers) {
        return new McpCompletionRegistry(handlers.getIfAvailable());
    }

    /**
     * 注册 Origin 策略：根据 {@code platform.component.mcp.server.allowed-origins}
     * 配置决定是否放行跨域请求；空列表表示全量放行。
     *
     * @param properties MCP 配置
     * @return Origin 校验策略
     */
    @Bean
    @ConditionalOnMissingBean
    public McpOriginPolicy mcpOriginPolicy(McpServerProperties properties) {
        return origin -> properties.getAllowedOrigins().isEmpty()
                || properties.getAllowedOrigins().contains(origin);
    }

    /**
     * 注册 MCP 实现信息（用于协议握手时声明 server 身份）。
     *
     * @param properties MCP 配置（{@code name/version/title/description/websiteUrl}）
     * @return MCP 实现信息
     */
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

    /**
     * 注册 MCP HTTP 端点：组合所有注册表与拦截器，对外暴露统一入口。
     *
     * @param registry Tool 注册表
     * @param implementationInfo 服务实现信息
     * @param originPolicy Origin 校验策略
     * @param resourceRegistry Resource 注册表
     * @param promptRegistry Prompt 注册表
     * @param completionRegistries 可选 Completion 注册表
     * @param invocationInterceptors 业务自定义的拦截器链（按 {@code @Order} 排序）
     * @param callContextFactory 调用上下文工厂
     * @return 组合后的 MCP HTTP 端点
     */
    @Bean
    @ConditionalOnMissingBean
    public McpServerHttpEndpoint mcpServerHttpEndpoint(
            McpToolRegistry registry,
            McpImplementationInfo implementationInfo,
            McpOriginPolicy originPolicy,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            ObjectProvider<McpCompletionRegistry> completionRegistries,
            ObjectProvider<McpToolInvocationInterceptor> invocationInterceptors,
            McpCallContextFactory callContextFactory) {
        return new McpServerHttpEndpoint(
                registry, implementationInfo, originPolicy, resourceRegistry, promptRegistry,
                completionRegistries.getIfAvailable(), invocationInterceptors.orderedStream().toList(),
                callContextFactory);
    }

    /**
     * 注册 Servlet MVC 控制器（仅当 Web 环境为 SERVLET 时生效）。
     *
     * @param endpoint 由 {@link #mcpServerHttpEndpoint} 提供的端点
     * @return Servlet 场景下的 HTTP 控制器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public McpServerHttpController mcpServerHttpController(McpServerHttpEndpoint endpoint) {
        return new McpServerHttpController(endpoint);
    }

    /**
     * 注册 WebFlux 控制器（仅当 Web 环境为 REACTIVE 时生效）。
     *
     * @param endpoint 由 {@link #mcpServerHttpEndpoint} 提供的端点
     * @return Reactive 场景下的 HTTP 控制器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnMissingBean
    public McpServerWebFluxController mcpServerWebFluxController(McpServerHttpEndpoint endpoint) {
        return new McpServerWebFluxController(endpoint);
    }

    /**
     * 注册 OAuth 元数据控制器（仅当
     * {@code platform.component.mcp.server.oauth.enabled=true} 时生效）。
     *
     * <p>必须显式配置 {@code platform.component.mcp.server.oauth.resource}；
     * 缺失时启动失败，避免运行时返回错误元数据。</p>
     *
     * @param properties MCP 配置（包含 {@code oauth.resource}）
     * @return OAuth 元数据控制器
     * @throws IllegalArgumentException 当 {@code oauth.resource} 未配置时
     */
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
