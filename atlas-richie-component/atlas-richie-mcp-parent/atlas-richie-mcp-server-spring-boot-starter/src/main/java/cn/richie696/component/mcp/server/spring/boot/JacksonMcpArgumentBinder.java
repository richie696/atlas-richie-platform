package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.server.McpArgumentBinder;
import cn.richie696.component.mcp.api.server.McpArgumentBindingException;
import cn.richie696.component.mcp.api.server.McpArgumentMetadata;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 默认的 {@link McpArgumentBinder} 实现，使用应用配置的 Jackson {@link ObjectMapper} 完成
 * {@code @McpArgument} 标注参数的绑定与类型转换。
 *
 * <p>该实现是 Spring Boot Starter 的默认注入；当业务侧需要自定义转换行为
 * （例如 Protocol Buffers、Money 类型、自定义日期格式）时，应自行提供 {@link McpArgumentBinder}
 * Bean 以替换本实现。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class JacksonMcpArgumentBinder implements McpArgumentBinder {
    private final ObjectMapper objectMapper;

    /**
     * 构造绑定器。
     *
     * @param objectMapper 已配置的 Jackson 映射器，不可为 {@code null}
     * @throws NullPointerException 当 {@code objectMapper} 为 {@code null} 时
     */
    public JacksonMcpArgumentBinder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 将 MCP 调用方传入的原始值转换为目标 Java 类型。
     *
     * <p>处理顺序：默认值回填 → 必填校验 → Optional 适配 → Jackson 类型转换；
     * 转换失败时抛出 {@link McpArgumentBindingException} 以便上层映射为 MCP 错误响应。</p>
     *
     * @param rawValue 来自 MCP 请求的原始值，可能为 {@code null}
     * @param targetType 目标参数化类型（用于处理 {@code Optional<T>}、{@code List<T>} 等）
     * @param metadata 参数元数据（名称、是否必填、默认值、敏感标记等）
     * @return 已转换的 Java 实例；当 {@code targetType} 为 {@link java.util.Optional} 且值为 {@code null} 时返回 {@link java.util.Optional#empty()}
     * @throws McpArgumentBindingException 当必填参数缺失，或 Jackson 转换失败时
     */
    @Override
    public Object bind(Object rawValue, Type targetType, McpArgumentMetadata metadata) {
        Object value = rawValue;
        if (value == null && metadata.defaultValue() != null
                && !McpArgument.NO_DEFAULT.equals(metadata.defaultValue())) {
            value = metadata.defaultValue();
        }
        if (value == null) {
            if (metadata.required()) {
                throw new McpArgumentBindingException(
                        metadata.name(), "Missing required tool argument: " + metadata.name());
            }
            if (java.util.Optional.class.isAssignableFrom(
                    objectMapper.constructType(targetType).getRawClass())) {
                return java.util.Optional.empty();
            }
            return null;
        }
        try {
            return objectMapper.convertValue(value, objectMapper.constructType(targetType));
        } catch (IllegalArgumentException exception) {
            throw new McpArgumentBindingException(
                    metadata.name(),
                    "Invalid value for tool argument: " + metadata.name(),
                    exception);
        }
    }
}
