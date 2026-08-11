package cn.richie696.component.mcp.api.server;

import java.lang.reflect.Type;

/**
 * Converts protocol-neutral input values to the declared Java/Kotlin method type.
 *
 * <p>MCP 协议以 {@code JSON} 表达参数，但业务方法可能声明为 {@code Integer}/{@code BigDecimal}/
 * {@code LocalDateTime}/{@code Enum}/{@code 自定义 POJO} 等任意 Java 类型。{@code McpArgumentBinder}
 * 负责从 {@code rawValue}（协议层原始值）转换为方法声明的 {@code targetType}。</p>
 *
 * <p>该接口作为业务扩展点：内置实现覆盖基础类型，需要注册自定义 POJO/Enum 绑定的业务可
 * 通过 Spring SPI 注册额外 binder。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpArgumentBinder {
    /**
     * 将协议层原始值转换为方法声明类型。
     *
     * @param rawValue 协议层原始值（Map/List/String/Number/Boolean）
     * @param targetType 业务方法声明的目标类型
     * @param metadata 与该参数对应的稳定元数据（含 format/enumValues/sensitive 等）
     * @return 已转换为目标类型的参数值
     * @throws McpArgumentBindingException 当无法完成转换时
     */
    Object bind(Object rawValue, Type targetType, McpArgumentMetadata metadata);
}
