package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpHeader;
import cn.richie696.component.mcp.api.annotation.McpTool;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Bridges stable MCP annotations to the framework-neutral Tool registry. */
public final class McpAnnotatedToolRegistrar {
    private final ApplicationContext applicationContext;

    public McpAnnotatedToolRegistrar(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void registerInto(McpToolRegistry registry) {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null
                    || McpToolRegistry.class.isAssignableFrom(beanType)
                    || McpToolRegistration.class.isAssignableFrom(beanType)
                    || beanType.getPackageName().startsWith("cn.richie696.component.mcp")) continue;
            Object bean = applicationContext.getBean(beanName);
            if (bean == this) continue;
            for (Method method : bean.getClass().getMethods()) {
                McpTool annotation = AnnotationUtils.findAnnotation(method, McpTool.class);
                if (annotation == null) continue;
                registry.register(new McpToolRegistration(descriptor(method, annotation), handler(bean, method)));
            }
        }
    }

    private cn.richie696.component.mcp.api.model.McpToolDescriptor descriptor(
            Method method,
            McpTool annotation) {
        String name = annotation.name().isBlank() ? method.getName() : annotation.name();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            McpArgument argument = parameter.getAnnotation(McpArgument.class);
            if (argument == null) continue;
            String argumentName = argument.name().isBlank() ? parameter.getName() : argument.name();
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", jsonType(parameter.getType()));
            if (!argument.description().isBlank()) schema.put("description", argument.description());
            McpHeader header = parameter.getAnnotation(McpHeader.class);
            if (header != null) schema.put("x-mcp-header", header.value());
            properties.put(argumentName, schema);
            if (argument.required()) required.add(argumentName);
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) inputSchema.put("required", required);
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("idempotent", annotation.idempotent());
        annotations.put("readOnly", annotation.readOnly());
        annotations.put("destructive", annotation.destructive());
        annotations.put("openWorld", annotation.openWorld());
        if (annotation.requiredScopes().length > 0) {
            annotations.put("requiredScopes", List.of(annotation.requiredScopes()));
        }
        return new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                name,
                annotation.title().isBlank() ? null : annotation.title(),
                annotation.description().isBlank() ? null : annotation.description(),
                inputSchema,
                Map.of(),
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
                        continue;
                    }
                    McpArgument argument = parameter.getAnnotation(McpArgument.class);
                    if (argument == null) {
                        throw new IllegalArgumentException("Annotated MCP tool parameter must use @McpArgument or McpCallContext");
                    }
                    String name = argument.name().isBlank() ? parameter.getName() : argument.name();
                    values[index] = convert(arguments.get(name), parameter.getType());
                }
                Object result = method.invoke(bean, values);
                if (result instanceof CompletionStage<?> stage) {
                    return stage.thenApply(this::response);
                }
                return java.util.concurrent.CompletableFuture.completedFuture(
                        result instanceof McpToolResponse response
                                ? response
                                : new McpToolResponse(List.of(), result, false));
            } catch (java.lang.reflect.InvocationTargetException exception) {
                return java.util.concurrent.CompletableFuture.failedFuture(exception.getCause());
            } catch (Throwable exception) {
                return java.util.concurrent.CompletableFuture.failedFuture(exception);
            }
        };
    }

    private McpToolResponse response(Object value) {
        return value instanceof McpToolResponse response
                ? response
                : new McpToolResponse(List.of(), value, false);
    }

    private Object convert(Object value, Class<?> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return value;
        if (type == String.class) return String.valueOf(value);
        if (type == int.class || type == Integer.class) return ((Number) value).intValue();
        if (type == long.class || type == Long.class) return ((Number) value).longValue();
        if (type == double.class || type == Double.class) return ((Number) value).doubleValue();
        if (type == boolean.class || type == Boolean.class) return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
        return value;
    }

    private String jsonType(Class<?> type) {
        if (type == String.class || type.isEnum()) return "string";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        }
        if (type.isArray() || java.util.Collection.class.isAssignableFrom(type)) return "array";
        return "object";
    }
}
