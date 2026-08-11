package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.McpProgressReporter;
import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpHeader;
import cn.richie696.component.mcp.api.annotation.McpTool;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpArgumentBinder;
import cn.richie696.component.mcp.api.server.McpArgumentMetadata;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.api.server.McpToolHandlerProvider;
import cn.richie696.component.mcp.schema.McpTypeSchemaGenerator;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationUtils;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 将 {@link McpTool} 注解桥接为框架中立的 {@link McpToolRegistration}。
 *
 * <p>该组件是 Spring 场景下 Tool 装配链的入口之一：扫描 {@link ApplicationContext} 中所有
 * 带 {@code @McpTool} 的方法，构建 {@link McpToolDescriptor} 与 {@link McpToolHandler}，
 * 再交由 {@link McpToolRegistrationAssembler} 与其它来源（编程式注册、
 * {@link McpToolHandlerProvider}、{@code tools.definitions}）合并。</p>
 *
 * <p>扫描过程会绕过框架自身（{@code cn.richie696.component.mcp.*} 包）以及
 * {@link McpToolRegistry}、{@link McpToolRegistration}、{@link McpToolHandlerProvider}
 * 三类元 Bean，避免递归注册。扫描结果按双重检查锁定缓存，
 * 仅当 {@link #updateProperties(McpServerProperties.Tools)} 改变了扫描范围时才失效。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpAnnotatedToolRegistrar {
    private final ApplicationContext applicationContext;
    private final McpArgumentBinder argumentBinder;
    private final McpTypeSchemaGenerator schemaGenerator;
    private volatile McpServerProperties.Tools properties;
    private volatile List<McpToolRegistration> cached;

    /**
     * 兼容历史命令式 API 的快捷构造器，使用安全的本地默认实现。
     *
     * <p>默认使用 {@link JacksonMcpArgumentBinder} + Jackson Schema Generator；
     * 仅供存量调用方使用，新代码应通过四参构造器显式注入依赖。</p>
     *
     * @param applicationContext Spring 应用上下文
     */
    public McpAnnotatedToolRegistrar(ApplicationContext applicationContext) {
        this(
                applicationContext,
                new JacksonMcpArgumentBinder(JsonMapper.builder().build()),
                new cn.richie696.component.mcp.schema.JacksonMcpTypeSchemaGenerator(),
                new McpServerProperties.Tools());
    }

    /**
     * 完整构造器（自动装配使用）。
     *
     * @param applicationContext Spring 应用上下文，用于扫描 Bean
     * @param argumentBinder MCP 参数绑定器
     * @param schemaGenerator JSON Schema 生成器
     * @param properties Tool 配置（包含扫描包、Bean 名等约束）
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public McpAnnotatedToolRegistrar(
            ApplicationContext applicationContext,
            McpArgumentBinder argumentBinder,
            McpTypeSchemaGenerator schemaGenerator,
            McpServerProperties.Tools properties) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        this.argumentBinder = Objects.requireNonNull(argumentBinder, "argumentBinder");
        this.schemaGenerator = Objects.requireNonNull(schemaGenerator, "schemaGenerator");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 拉取已缓存的注册项。
     *
     * <p>采用 DCL 单次初始化缓存，保证多次调用返回稳定的实例列表（identity 相等），
     * 这样 {@link McpToolRegistry} 在幂等刷新时无需重建视图。</p>
     *
     * @return 不可变的注册项列表
     */
    public List<McpToolRegistration> registrations() {
        List<McpToolRegistration> result = cached;
        if (result != null) return result;
        synchronized (this) {
            if (cached == null) cached = List.copyOf(scan());
            return cached;
        }
    }

    /**
     * 更新 Tool 配置；当扫描范围（包 / Bean 名）发生变更时清空缓存。
     *
     * @param properties 新 Tool 配置
     * @throws NullPointerException 当 {@code properties} 为 {@code null} 时
     */
    public synchronized void updateProperties(McpServerProperties.Tools properties) {
        Objects.requireNonNull(properties, "properties");
        if (!scanSignature(this.properties).equals(scanSignature(properties))) {
            cached = null;
        }
        this.properties = properties;
    }

    /**
     * 兼容旧版命令式注册入口：直接将扫描结果灌入 {@link McpToolRegistry}。
     *
     * @param registry 目标 Tool 注册表
     */
    public void registerInto(McpToolRegistry registry) {
        registrations().forEach(registry::register);
    }

    private List<McpToolRegistration> scan() {
        List<McpToolRegistration> result = new ArrayList<>();
        ConfigurableListableBeanFactory beanFactory = applicationContext
                instanceof ConfigurableApplicationContext configurable
                ? configurable.getBeanFactory()
                : null;
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            if (!eligibleBeanName(beanName)) continue;
            Class<?> beanType = targetType(beanName, beanFactory);
            if (!eligibleType(beanType)) continue;
            List<Method> methods = annotatedMethods(beanType);
            if (methods.isEmpty()) continue;
            Object bean = applicationContext.getBean(beanName);
            Class<?> runtimeType = AopProxyUtils.ultimateTargetClass(bean);
            for (Method candidate : methods) {
                Method method = BridgeMethodResolver.getMostSpecificMethod(candidate, runtimeType);
                method = BridgeMethodResolver.findBridgedMethod(method);
                McpTool annotation = AnnotationUtils.findAnnotation(method, McpTool.class);
                if (annotation == null) continue;
                Method invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
                result.add(new McpToolRegistration(
                        descriptor(method, annotation), handler(bean, invocable)));
            }
        }
        return result;
    }

    private Class<?> targetType(String beanName, ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory != null) {
            Class<?> target = org.springframework.aop.framework.autoproxy.AutoProxyUtils
                    .determineTargetClass(beanFactory, beanName);
            if (target != null) return target;
            return beanFactory.getType(beanName, false);
        }
        return applicationContext.getType(beanName);
    }

    private boolean eligibleBeanName(String beanName) {
        if (properties.getExcludeBeanNames().contains(beanName)) return false;
        return properties.getBeanNames().isEmpty() || properties.getBeanNames().contains(beanName);
    }

    private boolean eligibleType(Class<?> beanType) {
        if (beanType == null
                || McpToolRegistry.class.isAssignableFrom(beanType)
                || McpToolRegistration.class.isAssignableFrom(beanType)
                || McpToolHandlerProvider.class.isAssignableFrom(beanType)) return false;
        String packageName = beanType.getPackageName();
        if (packageName.startsWith("cn.richie696.component.mcp")) return false;
        if (properties.getExcludePackages().stream().anyMatch(packageName::startsWith)) return false;
        return properties.getScanPackages().isEmpty()
                || properties.getScanPackages().stream().anyMatch(packageName::startsWith);
    }

    private List<Method> annotatedMethods(Class<?> beanType) {
        List<Method> result = new ArrayList<>();
        for (Method method : beanType.getMethods()) {
            if (method.isBridge() || method.isSynthetic()) continue;
            if (AnnotationUtils.findAnnotation(method, McpTool.class) != null) result.add(method);
        }
        return result;
    }

    private McpToolDescriptor descriptor(Method method, McpTool annotation) {
        String name = annotation.name().isBlank() ? method.getName() : annotation.name();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        List<String> sensitiveArguments = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            if (contextParameter(parameter.getType())) continue;
            McpArgument argument = parameter.getAnnotation(McpArgument.class);
            if (argument == null) {
                throw new IllegalArgumentException(
                        "Annotated MCP tool parameter must use @McpArgument or be a supported context type: "
                                + method.toGenericString());
            }
            String argumentName = argument.name().isBlank() ? parameter.getName() : argument.name();
            Map<String, Object> schema = new LinkedHashMap<>(
                    schemaGenerator.generate(parameter.getParameterizedType()));
            enrichSchema(schema, argument);
            McpHeader header = parameter.getAnnotation(McpHeader.class);
            if (header != null) schema.put("x-mcp-header", header.value());
            properties.put(argumentName, schema);
            if (required(argument, parameter)) required.add(argumentName);
            if (argument.sensitive()) sensitiveArguments.add(argumentName);
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("additionalProperties", false);
        if (!required.isEmpty()) inputSchema.put("required", required);
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("idempotent", annotation.idempotent());
        annotations.put("readOnly", annotation.readOnly());
        annotations.put("destructive", annotation.destructive());
        annotations.put("openWorld", annotation.openWorld());
        annotations.put("enabled", annotation.enabled());
        annotations.put("audit", annotation.audit());
        if (annotation.requiredScopes().length > 0) {
            annotations.put("requiredScopes", List.of(annotation.requiredScopes()));
        }
        if (!sensitiveArguments.isEmpty()) {
            annotations.put("sensitiveArguments", List.copyOf(sensitiveArguments));
        }
        if (!annotation.group().isBlank()) annotations.put("group", annotation.group());
        if (annotation.timeoutMs() > 0) annotations.put("timeoutMs", annotation.timeoutMs());
        return new McpToolDescriptor(
                name,
                annotation.title().isBlank() ? null : annotation.title(),
                annotation.description().isBlank() ? null : annotation.description(),
                inputSchema,
                outputSchema(method),
                annotations);
    }

    private McpToolHandler handler(Object bean, Method method) {
        method.trySetAccessible();
        return (arguments, context) -> {
            try {
                Object[] values = new Object[method.getParameterCount()];
                Parameter[] parameters = method.getParameters();
                for (int index = 0; index < parameters.length; index++) {
                    Parameter parameter = parameters[index];
                    if (McpCallContext.class.isAssignableFrom(parameter.getType())) {
                        values[index] = context;
                    } else if (McpCancellationToken.class.isAssignableFrom(parameter.getType())) {
                        values[index] = context.cancellationToken();
                    } else if (McpProgressReporter.class.isAssignableFrom(parameter.getType())) {
                        values[index] = context.progressReporter();
                    } else {
                        McpArgument argument = parameter.getAnnotation(McpArgument.class);
                        String name = argument.name().isBlank() ? parameter.getName() : argument.name();
                        values[index] = argumentBinder.bind(
                                arguments.get(name),
                                parameter.getParameterizedType(),
                                metadata(name, argument, parameter));
                    }
                }
                Object value = method.invoke(bean, values);
                if (value instanceof CompletionStage<?> stage) {
                    return stage.thenApply(this::response);
                }
                if (value instanceof Mono<?> mono) {
                    return mono.toFuture().thenApply(this::response);
                }
                return CompletableFuture.completedFuture(response(value));
            } catch (InvocationTargetException exception) {
                return CompletableFuture.failedFuture(exception.getCause());
            } catch (Throwable exception) {
                return CompletableFuture.failedFuture(exception);
            }
        };
    }

    private McpToolResponse response(Object value) {
        return value instanceof McpToolResponse response
                ? response
                : new McpToolResponse(List.of(), value, false);
    }

    private McpArgumentMetadata metadata(String name, McpArgument argument, Parameter parameter) {
        return new McpArgumentMetadata(
                name,
                argument.description(),
                required(argument, parameter),
                argument.defaultValue(),
                argument.format(),
                argument.example(),
                List.of(argument.enumValues()),
                argument.sensitive());
    }

    private void enrichSchema(Map<String, Object> schema, McpArgument argument) {
        if (!argument.description().isBlank()) schema.put("description", argument.description());
        if (!argument.format().isBlank()) schema.put("format", argument.format());
        if (!argument.example().isBlank()) schema.put("examples", List.of(argument.example()));
        if (argument.enumValues().length > 0) schema.put("enum", List.of(argument.enumValues()));
        if (!McpArgument.NO_DEFAULT.equals(argument.defaultValue())) {
            schema.put("default", schemaDefault(argument.defaultValue(), schema.get("type")));
        }
        if (!argument.minimum().isBlank()) schema.put("minimum", number(argument.minimum(), "minimum"));
        if (!argument.maximum().isBlank()) schema.put("maximum", number(argument.maximum(), "maximum"));
        if (argument.minLength() >= 0) schema.put("minLength", argument.minLength());
        if (argument.maxLength() >= 0) schema.put("maxLength", argument.maxLength());
    }

    private Number number(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid @McpArgument " + field + ": " + value, exception);
        }
    }

    private boolean required(McpArgument argument, Parameter parameter) {
        boolean hasDefault = !McpArgument.NO_DEFAULT.equals(argument.defaultValue());
        // A primitive without a default cannot receive null through reflection, so it is
        // necessarily required. An explicit default makes a primitive safely optional.
        return argument.required() || (parameter.getType().isPrimitive() && !hasDefault);
    }

    private Object schemaDefault(String value, Object schemaType) {
        if ("integer".equals(schemaType) || "number".equals(schemaType)) {
            return number(value, "defaultValue");
        }
        if ("boolean".equals(schemaType)) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Invalid @McpArgument defaultValue: " + value);
            }
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private Map<String, Object> outputSchema(Method method) {
        Type type = method.getGenericReturnType();
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() instanceof Class<?> raw
                && (CompletionStage.class.isAssignableFrom(raw)
                        || Mono.class.isAssignableFrom(raw))) {
            type = parameterized.getActualTypeArguments()[0];
        }
        if (type == void.class || type == Void.class || type == McpToolResponse.class) {
            return Map.of();
        }
        return schemaGenerator.generate(type);
    }

    private boolean contextParameter(Class<?> type) {
        return McpCallContext.class.isAssignableFrom(type)
                || McpCancellationToken.class.isAssignableFrom(type)
                || McpProgressReporter.class.isAssignableFrom(type);
    }

    private List<Object> scanSignature(McpServerProperties.Tools tools) {
        return List.of(
                tools.getScanPackages(),
                tools.getExcludePackages(),
                tools.getBeanNames(),
                tools.getExcludeBeanNames());
    }
}
